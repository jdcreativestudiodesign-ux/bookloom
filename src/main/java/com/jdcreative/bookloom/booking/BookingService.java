package com.jdcreative.bookloom.booking;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.jdcreative.bookloom.booking.Catalog.ServiceItem;

/**
 * Booking rules: open days and hours, slot boundaries, no double booking, and a cancellation window.
 * Storage is in memory for the demo; swapping in a database only touches this class.
 */
@Service
public class BookingService {

    private static final String REF_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no 0/O or 1/I/L, easy to read out
    private final SecureRandom random = new SecureRandom();

    private final Catalog catalog;
    private final BusinessHours hours;
    private final Clock clock;
    private final Map<String, Booking> bookings = new ConcurrentHashMap<>();

    public BookingService(Catalog catalog, BusinessHours hours, Clock clock) {
        this.catalog = catalog;
        this.hours = hours;
        this.clock = clock;
    }

    public record NewBooking(String serviceId, String customerName, String email, LocalDateTime start) {
    }

    /** Start times still free for this service on this date, in order. Empty if closed or in the past. */
    public List<LocalTime> availability(String serviceId, LocalDate date) {
        ServiceItem service = requireService(serviceId);
        List<LocalTime> free = new ArrayList<>();
        if (!hours.isOpenOn(date.getDayOfWeek()) || date.isAfter(today().plusDays(BusinessHours.MAX_DAYS_AHEAD))) {
            return free;
        }
        LocalTime closes = hours.closesOn(date.getDayOfWeek()).orElseThrow();
        LocalDateTime now = LocalDateTime.now(clock);
        for (LocalTime t = BusinessHours.OPENS; fitsInDay(t, service, closes); t = t.plusMinutes(BusinessHours.SLOT_MINUTES)) {
            LocalDateTime start = date.atTime(t);
            LocalDateTime end = start.plusMinutes(service.durationMinutes());
            if (start.isAfter(now) && isFree(start, end)) {
                free.add(t);
            }
        }
        return free;
    }

    /** Creates a booking. Synchronised so two people can never take the same slot at the same moment. */
    public synchronized Booking book(NewBooking request) {
        ServiceItem service = requireService(request.serviceId());
        LocalDateTime start = request.start();
        LocalDateTime end = start.plusMinutes(service.durationMinutes());

        if (!start.isAfter(LocalDateTime.now(clock))) {
            throw BookingException.invalid("That time has already passed. Please pick a later slot.");
        }
        if (start.toLocalDate().isAfter(today().plusDays(BusinessHours.MAX_DAYS_AHEAD))) {
            throw BookingException.invalid("Bookings open " + BusinessHours.MAX_DAYS_AHEAD + " days ahead. Please pick an earlier date.");
        }
        if (!hours.isOpenOn(start.getDayOfWeek())) {
            throw BookingException.invalid("We're closed on " + BusinessHours.CLOSED_DAYS_TEXT + ".");
        }
        if (!hours.isOnSlotBoundary(start.toLocalTime())) {
            throw BookingException.invalid("Appointments start every " + BusinessHours.SLOT_MINUTES + " minutes (for example 10:00 or 10:15).");
        }
        LocalTime closes = hours.closesOn(start.getDayOfWeek()).orElseThrow();
        if (start.toLocalTime().isBefore(BusinessHours.OPENS) || !fitsInDay(start.toLocalTime(), service, closes)) {
            throw BookingException.invalid("That appointment would run outside opening hours ("
                    + BusinessHours.OPENS + " to " + closes + " that day).");
        }
        if (!isFree(start, end)) {
            throw BookingException.conflict("Sorry, that time was just taken. Please choose another slot.");
        }

        Booking booking = new Booking(newReference(), service.id(), request.customerName().strip(),
                request.email().strip().toLowerCase(), start, end, Booking.Status.CONFIRMED);
        bookings.put(booking.reference(), booking);
        return booking;
    }

    public Booking get(String reference) {
        Booking booking = bookings.get(reference.toUpperCase());
        if (booking == null) {
            throw BookingException.notFound("No booking found with reference " + reference + ".");
        }
        return booking;
    }

    /** Free cancellation up to 24 hours before; after that the customer is asked to call. */
    public synchronized Booking cancel(String reference) {
        Booking booking = get(reference);
        if (booking.status() == Booking.Status.CANCELLED) {
            throw BookingException.conflict("This booking is already cancelled.");
        }
        if (LocalDateTime.now(clock).plus(BusinessHours.FREE_CANCELLATION_NOTICE).isAfter(booking.start())) {
            throw BookingException.conflict("Online cancellation closes 24 hours before the appointment. Please call the studio.");
        }
        Booking cancelled = booking.cancelled();
        bookings.put(cancelled.reference(), cancelled);
        return cancelled;
    }

    private boolean isFree(LocalDateTime start, LocalDateTime end) {
        return bookings.values().stream().noneMatch(b -> b.overlaps(start, end));
    }

    private boolean fitsInDay(LocalTime start, ServiceItem service, LocalTime closes) {
        // Compare in minutes so a late start can't wrap past midnight and look "early".
        int startMin = start.toSecondOfDay() / 60;
        return startMin + service.durationMinutes() <= closes.toSecondOfDay() / 60;
    }

    private ServiceItem requireService(String serviceId) {
        return catalog.find(serviceId)
                .orElseThrow(() -> BookingException.notFound("We don't offer a service called '" + serviceId + "'."));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private String newReference() {
        String ref;
        do {
            StringBuilder sb = new StringBuilder("VH-");
            for (int i = 0; i < 6; i++) {
                sb.append(REF_ALPHABET.charAt(random.nextInt(REF_ALPHABET.length())));
            }
            ref = sb.toString();
        } while (bookings.containsKey(ref));
        return ref;
    }
}
