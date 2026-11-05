-- Read-only consistency report. No personal identifiers or payment keys are printed.
SELECT 'remaining_out_of_range' AS check_name,count(*) AS violations FROM slot WHERE remaining<0 OR remaining>capacity
UNION ALL SELECT 'duplicate_active_seat',count(*) FROM (SELECT seat_id FROM reservation WHERE status IN ('HELD','CONFIRMED') AND seat_id IS NOT NULL GROUP BY seat_id HAVING count(*)>1) duplicate_seats
UNION ALL SELECT 'duplicate_slot_time',count(*) FROM (SELECT starts_at FROM slot GROUP BY starts_at HAVING count(*)>1) duplicate_times
UNION ALL SELECT 'remaining_mismatch',count(*) FROM slot s WHERE s.remaining<>s.capacity-(SELECT count(*) FROM reservation r WHERE r.slot_id=s.id AND r.status IN ('HELD','CONFIRMED'))
UNION ALL SELECT 'seat_flag_mismatch',count(*) FROM seat s WHERE s.reserved<>EXISTS(SELECT 1 FROM reservation r WHERE r.seat_id=s.id AND r.status IN ('HELD','CONFIRMED'))
UNION ALL SELECT 'cancelled_payment_with_active_seats',count(*) FROM payment p WHERE p.status='CANCELLED' AND EXISTS(SELECT 1 FROM reservation r WHERE r.order_id=p.order_id AND r.status IN ('HELD','CONFIRMED'));
