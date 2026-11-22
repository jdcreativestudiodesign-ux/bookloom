package com.jdcreative.bookloom.enquiry;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

/** Stores contact-form enquiries. A real client would forward these by email or to a CRM. */
@Service
public class EnquiryService {

    public record Enquiry(long id, String name, String email, String topic, String message, Instant receivedAt) {
    }

    private final Clock clock;
    private final AtomicLong ids = new AtomicLong();
    private final List<Enquiry> inbox = new CopyOnWriteArrayList<>();

    public EnquiryService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Saves a genuine enquiry. Bots fill in the hidden "website" field that people never see;
     * those are accepted quietly (so the bot learns nothing) but never stored.
     */
    public long receive(String name, String email, String topic, String message, String honeypot) {
        long id = ids.incrementAndGet();
        if (honeypot != null && !honeypot.isBlank()) {
            return id;
        }
        inbox.add(new Enquiry(id, name.strip(), email.strip().toLowerCase(), topic, message.strip(), Instant.now(clock)));
        return id;
    }

    public List<Enquiry> inbox() {
        return List.copyOf(inbox);
    }
}
