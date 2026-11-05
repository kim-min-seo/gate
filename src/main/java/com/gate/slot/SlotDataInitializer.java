package com.gate.slot;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.*;
import java.util.*;

@Configuration
public class SlotDataInitializer {
    private final SlotRepository slots;
    public SlotDataInitializer(SlotRepository slots){this.slots=slots;}
    @Bean CommandLineRunner seedSlots(){return args -> maintainSlots();}
    @Scheduled(cron="0 0 0 * * *",zone="Asia/Seoul")
    public void maintainSlots(){
        var today=LocalDate.now(ZoneId.of("Asia/Seoul"));
        var existing=new HashSet<>(slots.findAll().stream().map(Slot::getStartsAt).toList());
        var missing=new ArrayList<Slot>();
        for(var date=today;date.isBefore(today.plusMonths(6));date=date.plusDays(1)){
            for(int hour=9;hour<=18;hour++){
                var starts=date.atTime(hour,0);
                if(!existing.contains(starts)) missing.add(new Slot(hour+"시 입장",100,starts));
            }
        }
        slots.saveAll(missing);
    }
}
