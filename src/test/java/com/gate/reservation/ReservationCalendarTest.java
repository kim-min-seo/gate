package com.gate.reservation;
import com.gate.slot.*;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationCalendarTest {
    @Test void hidesPastAndBeyondSixMonthSlotsAndDefaultsToAvailableDate(){
        var service=mock(SlotService.class);
        var today=LocalDate.now();
        var future=new Slot("future",100,today.plusDays(1).atTime(10,0));
        when(service.findAll()).thenReturn(List.of(new Slot("past",100,today.minusDays(1).atTime(10,0)),future,
                new Slot("beyond",100,today.plusMonths(6).atTime(10,0))));
        var model=new ExtendedModelMap();
        assertEquals("slots/list",new SlotController(service).list(today.minusMonths(1).toString(),model));
        assertEquals(List.of(future),model.get("slots"));
        assertEquals(List.of(today.plusDays(1)),model.get("dates"));
        assertEquals(today.plusDays(1),model.get("selectedDate"));
    }
    @Test void generatesTenTimesPerDateForSixMonths(){
        var repo=mock(SlotRepository.class);when(repo.findAll()).thenReturn(List.of());
        var captured=new ArrayList<Slot>();
        when(repo.saveAll(any())).thenAnswer(call->{Iterable<Slot> input=call.getArgument(0);input.forEach(captured::add);return captured;});
        new SlotDataInitializer(repo).maintainSlots();
        var today=LocalDate.now(ZoneId.of("Asia/Seoul"));
        assertEquals(java.time.temporal.ChronoUnit.DAYS.between(today,today.plusMonths(6))*10,captured.size());
        assertEquals(today.atTime(9,0),captured.getFirst().getStartsAt());
        assertEquals(today.plusMonths(6).minusDays(1).atTime(18,0),captured.getLast().getStartsAt());
    }
}
