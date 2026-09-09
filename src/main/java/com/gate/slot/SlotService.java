package com.gate.slot;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class SlotService {
    private final SlotRepository slotRepository;

    public SlotService(SlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    public List<Slot> findAll() {
        return slotRepository.findAllByOrderByStartsAtAsc();
    }
}
