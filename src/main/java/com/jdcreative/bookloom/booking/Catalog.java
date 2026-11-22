package com.jdcreative.bookloom.booking;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** The services on offer. Kept in code for the demo; a real client would edit these in a database or CMS. */
@Component
public class Catalog {

    /** {@code priceFrom} is true when the price is a starting price ("From $95"), as on the website. */
    public record ServiceItem(String id, String name, int durationMinutes, BigDecimal price, boolean priceFrom) {
    }

    // Same menu as the Velloway Hair Studio website (prices in US dollars).
    private final List<ServiceItem> items = List.of(
            new ServiceItem("cut", "Cut and finish", 60, new BigDecimal("55.00"), false),
            new ServiceItem("colour", "Colour", 90, new BigDecimal("95.00"), true),
            new ServiceItem("blow-dry", "Blow-dry", 45, new BigDecimal("40.00"), false),
            new ServiceItem("treatment", "Treatment", 30, new BigDecimal("35.00"), false));

    public List<ServiceItem> all() {
        return items;
    }

    public Optional<ServiceItem> find(String id) {
        return items.stream().filter(s -> s.id().equals(id)).findFirst();
    }
}
