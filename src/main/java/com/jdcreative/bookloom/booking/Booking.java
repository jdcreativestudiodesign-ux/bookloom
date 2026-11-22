package com.jdcreative.bookloom.booking;

import java.time.LocalDateTime;

public record Booking(
        String reference,
        String serviceId,
        String customerName,
        String email,
        LocalDateTime start,
        LocalDateTime end,
        Status status) {

    public enum Status { CONFIRMED, CANCELLED }

    public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
        return status == Status.CONFIRMED && start.isBefore(otherEnd) && otherStart.isBefore(end);
    }

    public Booking cancelled() {
        return new Booking(reference, serviceId, customerName, email, start, end, Status.CANCELLED);
    }
}
