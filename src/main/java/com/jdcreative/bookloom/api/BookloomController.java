package com.jdcreative.bookloom.api;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jdcreative.bookloom.api.Requests.BookingRequest;
import com.jdcreative.bookloom.api.Requests.EnquiryRequest;
import com.jdcreative.bookloom.booking.Booking;
import com.jdcreative.bookloom.booking.BookingService;
import com.jdcreative.bookloom.booking.BookingService.NewBooking;
import com.jdcreative.bookloom.booking.Catalog;
import com.jdcreative.bookloom.booking.Catalog.ServiceItem;
import com.jdcreative.bookloom.enquiry.EnquiryService;

@RestController
@RequestMapping("/api")
public class BookloomController {

    private final Catalog catalog;
    private final BookingService bookings;
    private final EnquiryService enquiries;

    public BookloomController(Catalog catalog, BookingService bookings, EnquiryService enquiries) {
        this.catalog = catalog;
        this.bookings = bookings;
        this.enquiries = enquiries;
    }

    @GetMapping("/services")
    public List<ServiceItem> services() {
        return catalog.all();
    }

    @GetMapping("/availability")
    public Map<String, Object> availability(
            @RequestParam String serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<LocalTime> free = bookings.availability(serviceId, date);
        return Map.of("serviceId", serviceId, "date", date.toString(), "slots", free.stream().map(LocalTime::toString).toList());
    }

    @PostMapping("/bookings")
    public ResponseEntity<Booking> book(@Valid @RequestBody BookingRequest body) {
        Booking booking = bookings.book(new NewBooking(body.serviceId(), body.name(), body.email(), body.start()));
        return ResponseEntity.created(URI.create("/api/bookings/" + booking.reference())).body(booking);
    }

    @GetMapping("/bookings/{reference}")
    public Booking booking(@PathVariable String reference) {
        return bookings.get(reference);
    }

    @DeleteMapping("/bookings/{reference}")
    public Booking cancel(@PathVariable String reference) {
        return bookings.cancel(reference);
    }

    @PostMapping("/enquiries")
    public ResponseEntity<Map<String, Object>> enquire(@Valid @RequestBody EnquiryRequest body) {
        long id = enquiries.receive(body.name(), body.email(), body.topicOrDefault(), body.message(), body.website());
        return ResponseEntity.status(201).body(Map.of(
                "id", id,
                "message", "Thanks! We usually reply within one working day."));
    }
}
