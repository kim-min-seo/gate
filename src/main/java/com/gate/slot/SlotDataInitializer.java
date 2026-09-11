package com.gate.slot;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDateTime;
import com.gate.seat.Seat;
import com.gate.seat.SeatRepository;

@Configuration
public class SlotDataInitializer {
    @Bean
    CommandLineRunner seedSlots(SlotRepository repository, SeatRepository seatRepository) {
        return args -> {
            if (repository.count() == 0) {
                for (Slot slot : java.util.List.of(
                        repository.save(new Slot("10시 입장", 100, LocalDateTime.of(2026, 9, 20, 10, 0))),
                        repository.save(new Slot("14시 입장", 100, LocalDateTime.of(2026, 9, 20, 14, 0))),
                        repository.save(new Slot("18시 입장", 100, LocalDateTime.of(2026, 9, 20, 18, 0))))) {
                    for (int row = 1; row <= 10; row++) for (char col = 'A'; col <= 'J'; col++)
                        seatRepository.save(new Seat(row + "-" + col, slot));
                }
            }
            for (Slot slot : repository.findAll()) {
                for (int row = 1; row <= 10; row++) for (char col = 'A'; col <= 'J'; col++) {
                    String label = row + "-" + col;
                    if (!seatRepository.existsBySlotAndLabel(slot, label)) seatRepository.save(new Seat(label, slot));
                }
            }
            LocalDateTime base = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0).withNano(0);
            int[] hours = {9,10,11,12,13,14,15,16,17,18};
            for (int day = 0; day < 30; day++) for (int hour : hours) {
                LocalDateTime starts = base.plusDays(day).withHour(hour);
                if (!repository.existsByStartsAt(starts)) {
                    Slot slot = repository.save(new Slot(hour + "시 입장", 100, starts));
                    for (int row = 1; row <= 10; row++) for (char col = 'A'; col <= 'J'; col++) seatRepository.save(new Seat(row + "-" + col, slot));
                }
            }
        };
    }
}
