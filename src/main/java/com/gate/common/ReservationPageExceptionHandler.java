package com.gate.common;

import com.gate.reservation.ReservationController;
import com.gate.reservation.PaymentController;
import com.gate.seat.SeatController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.NoSuchElementException;

@ControllerAdvice(assignableTypes={ReservationController.class,PaymentController.class,SeatController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReservationPageExceptionHandler {
    @ExceptionHandler({MissingServletRequestParameterException.class,MethodArgumentTypeMismatchException.class,NoSuchElementException.class})
    public String invalidRequest(RedirectAttributes attributes){
        attributes.addFlashAttribute("message","선택한 예약 정보를 찾을 수 없습니다. 날짜와 좌석을 다시 선택해 주세요.");
        return "redirect:/slots";
    }
}
