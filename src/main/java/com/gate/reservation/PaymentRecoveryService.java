package com.gate.reservation;

import com.gate.slot.SlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.*;

@Service
public class PaymentRecoveryService {
    private final PaymentRecoveryRepository jobs;
    private final ReservationRepository reservations;
    private final PaymentRepository payments;
    private final SlotRepository slots;
    private final TossPaymentService gateway;
    public PaymentRecoveryService(PaymentRecoveryRepository jobs, ReservationRepository reservations,
            PaymentRepository payments, SlotRepository slots, TossPaymentService gateway){
        this.jobs=jobs;this.reservations=reservations;this.payments=payments;this.slots=slots;this.gateway=gateway;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void prepare(String orderId,String paymentKey,Long userId,Long slotId,int amount,List<Long> seatIds){
        slots.findByIdForUpdate(slotId).orElseThrow();
        var existing=jobs.findById(orderId).orElse(null);
        if(existing!=null){
            if(!existing.getUserId().equals(userId)||!existing.getPaymentKey().equals(paymentKey)||existing.getAmount()!=amount)
                throw new IllegalStateException("결제 요청 정보가 일치하지 않습니다.");
            return;
        }
        var held=reservations.findByOrderIdAndUserId(orderId,userId);
        if(held.isEmpty() || held.size()>4 || held.size()*10000!=amount
                || held.stream().anyMatch(r->r.getStatus()!=ReservationStatus.HELD||r.isExpired())
                || !new HashSet<>(seatIds).equals(new HashSet<>(held.stream().map(r->r.getSeat().getId()).toList())))
            throw new IllegalStateException("유효한 좌석 점유를 찾을 수 없습니다.");
        jobs.saveAndFlush(new PaymentRecovery(orderId,paymentKey,userId,slotId,amount));
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void resolve(String orderId){
        var job=jobs.findById(orderId).orElseThrow();
        slots.findByIdForUpdate(job.getSlotId()).orElseThrow();
        if(em!=null) em.refresh(job);
        if(!job.isCancellationRequested()) job.resolve();
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void prepareCancellation(Long reservationId, Long userId){
        var item=reservations.findByIdAndUserId(reservationId,userId).orElseThrow();
        slots.findByIdForUpdate(item.getSlot().getId()).orElseThrow();
        if(em!=null) em.refresh(item);
        if(!item.getSlot().getStartsAt().isAfter(java.time.LocalDateTime.now())) throw new IllegalStateException("입장 시간이 지난 예약입니다.");
        if(item.getStatus()!=ReservationStatus.HELD && item.getStatus()!=ReservationStatus.CONFIRMED) throw new IllegalStateException("이미 취소되었거나 만료된 예약입니다.");
        if(item.getOrderId()==null) return;
        var job=jobs.findById(item.getOrderId()).orElse(null);
        var local=payments.findByOrderId(item.getOrderId()).orElse(null);
        if(job==null && local==null) return;
        if(job==null) job=new PaymentRecovery(local.getOrderId(),local.getPaymentKey(),userId,item.getSlot().getId(),local.getAmount());
        if(!job.isCancellationRequested()) job.requestCancellation();
        jobs.saveAndFlush(job);
    }

    @Transactional
    public void reconcile(String orderId){
        var snapshot=jobs.findById(orderId).orElseThrow();
        slots.findByIdForUpdate(snapshot.getSlotId()).orElseThrow();
        // Refresh after acquiring the same slot lock used by approval/cancellation.
        reconcileLocked(snapshot);
    }
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
    private void reconcileLocked(PaymentRecovery job){
        if(em!=null) em.refresh(job);
        if(!"PENDING".equals(job.getStatus())) return;
        var local=payments.findByOrderId(job.getOrderId()).orElse(null);
        if(local!=null && !job.isCancellationRequested()){
            if(local.getPaymentKey().equals(job.getPaymentKey()) && local.getUserId().equals(job.getUserId()) && local.getAmount()==job.getAmount()) job.resolve();
            else job.manual("LOCAL_PAYMENT_MISMATCH");
            return;
        }
        try {
            var remote=gateway.find(job.getPaymentKey());
            if(remote==null || !job.getOrderId().equals(remote.get("orderId")) || !job.getPaymentKey().equals(remote.get("paymentKey"))
                    || !(remote.get("totalAmount") instanceof Number total) || total.longValue()!=job.getAmount()){
                job.manual("GATEWAY_PAYMENT_MISMATCH");return;
            }
            var held=reservations.findByOrderIdAndUserId(job.getOrderId(),job.getUserId());
            String status=String.valueOf(remote.get("status"));
            if(job.isCancellationRequested()){
                if("DONE".equals(status)){
                    var cancelled=gateway.cancel(job.getPaymentKey(),"Gate requested order cancellation recovery");
                    if(cancelled==null||!"CANCELED".equals(cancelled.get("status"))){job.retry("CANCELLATION_NOT_CONFIRMED");return;}
                } else if(!Set.of("CANCELED","ABORTED","EXPIRED").contains(status)) {job.retry("CANCELLATION_NOT_CONFIRMED");return;}
                if(local==null) {if("DONE".equals(status)||"CANCELED".equals(status)) saveCancelledPayment(job);}
                else local.cancel();
                releaseCancelledOrder(held);job.resolve();return;
            }
            if("DONE".equals(status)){
                boolean valid=held.size()*10000==job.getAmount() && !held.isEmpty()
                        && held.stream().allMatch(r->r.getStatus()==ReservationStatus.HELD&&!r.isExpired());
                if(valid){
                    held.forEach(Reservation::confirm);
                    payments.save(new Payment(job.getPaymentKey(),job.getOrderId(),job.getAmount(),job.getUserId(),job.getSlotId()));
                    job.resolve();
                } else {
                    var cancelled=gateway.cancel(job.getPaymentKey(),"Reservation recovery: seats unavailable");
                    if(cancelled==null||!"CANCELED".equals(cancelled.get("status"))) {job.retry("CANCELLATION_NOT_CONFIRMED");return;}
                    saveCancelledPayment(job);release(held);job.resolve();
                }
            } else if(Set.of("CANCELED","ABORTED","EXPIRED").contains(status)){
                if("CANCELED".equals(status)) saveCancelledPayment(job);
                release(held);job.resolve();
            } else job.retry("PAYMENT_NOT_TERMINAL");
        } catch(RuntimeException failure){
            // Do not persist raw provider messages, credentials or card details.
            job.retry("GATEWAY_UNAVAILABLE");
        }
    }
    private void releaseCancelledOrder(List<Reservation> items){
        for(var item:items) if(item.getStatus()==ReservationStatus.HELD || item.getStatus()==ReservationStatus.CONFIRMED){
            item.cancel();if(item.getSeat()!=null)item.getSeat().release();item.getSlot().increaseRemaining();
        }
    }
    private void saveCancelledPayment(PaymentRecovery job){
        var payment=new Payment(job.getPaymentKey(),job.getOrderId(),job.getAmount(),job.getUserId(),job.getSlotId());
        payment.cancel();payments.save(payment);
    }
    private void release(List<Reservation> held){
        for(var item:held) if(item.getStatus()==ReservationStatus.HELD){
            item.cancel();if(item.getSeat()!=null)item.getSeat().release();item.getSlot().increaseRemaining();
        }
    }
}
