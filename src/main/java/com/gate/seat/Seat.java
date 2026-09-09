package com.gate.seat;

import com.gate.slot.Slot;
import jakarta.persistence.*;

@Entity
public class Seat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String label;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Slot slot;
    private boolean reserved;
    protected Seat() {}
    public Seat(String label, Slot slot) { this.label = label; this.slot = slot; }
    public Long getId() { return id; }
    public String getLabel() { return label; }
    public Slot getSlot() { return slot; }
    public boolean isReserved() { return reserved; }
    public void reserve() { reserved = true; }
    public void release() { reserved = false; }
}
