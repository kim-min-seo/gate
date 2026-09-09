package com.gate.slot;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class Slot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private int capacity;
    private int remaining;
    private LocalDateTime startsAt;

    protected Slot() {
    }

    public Slot(String name, int capacity, LocalDateTime startsAt) {
        this.name = name;
        this.capacity = capacity;
        this.remaining = capacity;
        this.startsAt = startsAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public int getCapacity() { return capacity; }
    public int getRemaining() { return remaining; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void decreaseRemaining() { this.remaining--; }
    public void increaseRemaining() { if (remaining < capacity) this.remaining++; }
}
