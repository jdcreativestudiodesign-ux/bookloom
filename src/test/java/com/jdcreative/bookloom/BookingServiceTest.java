package com.jdcreative.bookloom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.jdcreative.bookloom.booking.Booking;
import com.jdcreative.bookloom.booking.BookingException;
import com.jdcreative.bookloom.booking.BookingService;
import com.jdcreative.bookloom.booking.BookingService.NewBooking;
import com.jdcreative.bookloom.booking.BusinessHours;
import com.jdcreative.bookloom.booking.Catalog;
import com.jdcreative.bookloom.enquiry.EnquiryService;

/** Booking rules, tested without starting Spring. "Now" is fixed at Tuesday 1 Dec 2026, 08:00 New York time (the studio's own time zone). */
class BookingServiceTest {

    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    static final LocalDate TUESDAY = LocalDate.of(2026, 12, 1);
    static final LocalDate WEDNESDAY = LocalDate.of(2026, 12, 2);
    static final LocalDate SATURDAY = LocalDate.of(2026, 12, 5);
    static final LocalDate SUNDAY = LocalDate.of(2026, 12, 6);
    static final LocalDate MONDAY = LocalDate.of(2026, 12, 7);

    BookingService service;

    static Clock clockAt(LocalDateTime now) {
        return Clock.fixed(ZonedDateTime.of(now, NEW_YORK).toInstant(), NEW_YORK);
    }

    @BeforeEach
    void setUp() {
        service = new BookingService(new Catalog(), new BusinessHours(), clockAt(TUESDAY.atTime(8, 0)));
    }

    Booking book(String serviceId, LocalDateTime start) {
        return service.book(new NewBooking(serviceId, "Sam Rivera", "Sam@Example.com ", start));
    }

    static void assertRule(Runnable action, HttpStatus status, String messagePart) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BookingException.class, e -> {
                    assertThat(e.status()).isEqualTo(status);
                    assertThat(e.getMessage()).contains(messagePart);
                });
    }

    @Test
    @DisplayName("Opening rules match the website: Tue-Fri 09:00-19:00, Sat 09:00-16:00, closed Sun and Mon")
    void openingRulesArePinned() {
        BusinessHours hours = new BusinessHours();
        assertThat(BusinessHours.OPENS).isEqualTo(LocalTime.of(9, 0));
        assertThat(hours.closesOn(TUESDAY.getDayOfWeek())).contains(LocalTime.of(19, 0));
        assertThat(hours.closesOn(LocalDate.of(2026, 12, 4).getDayOfWeek())).contains(LocalTime.of(19, 0)); // Friday
        assertThat(hours.closesOn(SATURDAY.getDayOfWeek())).contains(LocalTime.of(16, 0));
        assertThat(hours.isOpenOn(SUNDAY.getDayOfWeek())).isFalse();
        assertThat(hours.isOpenOn(MONDAY.getDayOfWeek())).isFalse();
        assertThat(BusinessHours.SLOT_MINUTES).isEqualTo(15);
        assertThat(BusinessHours.MAX_DAYS_AHEAD).isEqualTo(60);
        assertThat(BusinessHours.FREE_CANCELLATION_NOTICE.toHours()).isEqualTo(24);
    }

    @Test
    @DisplayName("A free day offers every slot where the whole appointment fits before closing")
    void fullDayAvailability() {
        List<LocalTime> slots = service.availability("cut", WEDNESDAY); // 60 minutes, closes 19:00
        assertThat(slots).first().isEqualTo(LocalTime.of(9, 0));
        assertThat(slots).last().isEqualTo(LocalTime.of(18, 0));
        assertThat(slots).hasSize(37);
        List<LocalTime> saturday = service.availability("cut", SATURDAY); // closes 16:00
        assertThat(saturday).last().isEqualTo(LocalTime.of(15, 0));
        assertThat(saturday).hasSize(25);
    }

    @Test
    @DisplayName("A booking removes every slot that would overlap it, and nothing else")
    void bookingBlocksOverlaps() {
        book("cut", WEDNESDAY.atTime(10, 0)); // 10:00-11:00
        List<LocalTime> slots = service.availability("cut", WEDNESDAY);
        assertThat(slots).contains(LocalTime.of(9, 0), LocalTime.of(11, 0)); // end touches start: fine
        assertThat(slots).doesNotContain(LocalTime.of(9, 15), LocalTime.of(10, 0), LocalTime.of(10, 45));
    }

    @Test
    @DisplayName("Today's slots that have already started are not offered")
    void pastSlotsHidden() {
        service = new BookingService(new Catalog(), new BusinessHours(), clockAt(TUESDAY.atTime(12, 10)));
        assertThat(service.availability("treatment", TUESDAY)).first().isEqualTo(LocalTime.of(12, 15));
    }

    @Test
    @DisplayName("Closed days and dates past the booking window show no slots")
    void closedAndFarDatesEmpty() {
        assertThat(service.availability("cut", SUNDAY)).isEmpty();
        assertThat(service.availability("cut", MONDAY)).isEmpty();
        assertThat(service.availability("cut", TUESDAY.plusDays(63))).isEmpty(); // Tue 2 Feb 2027: open, but too far ahead
        assertThat(service.availability("cut", TUESDAY.plusDays(60))).isNotEmpty(); // Sat 30 Jan 2027
    }

    @Test
    @DisplayName("A confirmed booking gets a readable reference and a tidy email")
    void successfulBooking() {
        Booking b = book("colour", WEDNESDAY.atTime(17, 30)); // 90 minutes, ends exactly at closing
        assertThat(b.reference()).matches("VH-[A-HJ-NP-Z2-9]{6}");
        assertThat(b.end()).isEqualTo(WEDNESDAY.atTime(19, 0));
        assertThat(book("colour", SATURDAY.atTime(14, 30)).end()).isEqualTo(SATURDAY.atTime(16, 0)); // Saturday closing
        assertThat(b.email()).isEqualTo("sam@example.com");
        assertThat(b.status()).isEqualTo(Booking.Status.CONFIRMED);
        assertThat(service.get(b.reference().toLowerCase())).isEqualTo(b);
    }

    @Test
    @DisplayName("Business rules are enforced with customer-friendly messages")
    void ruleViolations() {
        assertRule(() -> book("cut", TUESDAY.atTime(7, 45)), HttpStatus.UNPROCESSABLE_CONTENT, "already passed");
        assertRule(() -> book("cut", SUNDAY.atTime(10, 0)), HttpStatus.UNPROCESSABLE_CONTENT, "closed on Sundays and Mondays");
        assertRule(() -> book("cut", MONDAY.atTime(10, 0)), HttpStatus.UNPROCESSABLE_CONTENT, "closed on Sundays and Mondays");
        assertRule(() -> book("cut", WEDNESDAY.atTime(10, 5)), HttpStatus.UNPROCESSABLE_CONTENT, "every 15 minutes");
        assertRule(() -> book("cut", WEDNESDAY.atTime(8, 45)), HttpStatus.UNPROCESSABLE_CONTENT, "outside opening hours");
        assertRule(() -> book("colour", WEDNESDAY.atTime(17, 45)), HttpStatus.UNPROCESSABLE_CONTENT, "09:00 to 19:00");
        assertRule(() -> book("colour", SATURDAY.atTime(14, 45)), HttpStatus.UNPROCESSABLE_CONTENT, "09:00 to 16:00");
        assertRule(() -> book("cut", LocalDate.of(2027, 2, 2).atTime(10, 0)), HttpStatus.UNPROCESSABLE_CONTENT, "60 days ahead");
        assertRule(() -> book("massage", WEDNESDAY.atTime(10, 0)), HttpStatus.NOT_FOUND, "massage");
    }

    @Test
    @DisplayName("An overlapping booking is refused with 409")
    void doubleBookingRefused() {
        book("cut", WEDNESDAY.atTime(10, 0));
        assertRule(() -> book("treatment", WEDNESDAY.atTime(10, 30)), HttpStatus.CONFLICT, "just taken");
        assertThat(book("treatment", WEDNESDAY.atTime(11, 0)).start()).isEqualTo(WEDNESDAY.atTime(11, 0));
    }

    @Test
    @DisplayName("400 races of 8 people grabbing the same slot at once: every slot is won exactly once")
    void concurrentBookingsNeverDoubleBook() throws Exception {
        int racers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        try {
            List<LocalDateTime> slots = new ArrayList<>();
            for (LocalDate day = WEDNESDAY; slots.size() < 400; day = day.plusDays(1)) {
                BusinessHours hours = new BusinessHours();
                if (!hours.isOpenOn(day.getDayOfWeek())) {
                    continue;
                }
                LocalTime closes = hours.closesOn(day.getDayOfWeek()).orElseThrow();
                // Treatments last 30 minutes, so every 30-minute start is a separate, bookable slot.
                for (LocalTime t = BusinessHours.OPENS; !t.plusMinutes(30).isAfter(closes) && slots.size() < 400; t = t.plusMinutes(30)) {
                    slots.add(day.atTime(t));
                }
            }
            int doubleBooked = 0;
            for (LocalDateTime slot : slots) {
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Boolean>> results = new ArrayList<>();
                for (int i = 0; i < racers; i++) {
                    results.add(pool.submit(() -> {
                        go.await();
                        try {
                            book("treatment", slot);
                            return true;
                        } catch (BookingException e) {
                            return false;
                        }
                    }));
                }
                go.countDown();
                int wins = 0;
                for (Future<Boolean> f : results) {
                    wins += f.get(10, TimeUnit.SECONDS) ? 1 : 0;
                }
                assertThat(wins).as("winners for %s", slot).isGreaterThanOrEqualTo(1);
                doubleBooked += wins > 1 ? 1 : 0;
            }
            assertThat(doubleBooked).as("slots booked by more than one person").isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Cancelling more than 24 hours ahead frees the slot again")
    void cancelFreesSlot() {
        Booking b = book("cut", WEDNESDAY.atTime(9, 0)); // 25 hours away
        assertThat(service.cancel(b.reference()).status()).isEqualTo(Booking.Status.CANCELLED);
        assertThat(service.availability("cut", WEDNESDAY)).contains(LocalTime.of(9, 0));
        assertRule(() -> service.cancel(b.reference()), HttpStatus.CONFLICT, "already cancelled");
    }

    @Test
    @DisplayName("Inside 24 hours, online cancellation is refused and the customer is asked to call")
    void lateCancelRefused() {
        Booking soon = book("treatment", TUESDAY.atTime(16, 0));          // 8 hours away
        Booking edge = book("treatment", WEDNESDAY.atTime(8, 0).plusHours(1)); // Wed 09:00, 25 hours away
        assertRule(() -> service.cancel(soon.reference()), HttpStatus.CONFLICT, "call the studio");
        assertThat(service.get(soon.reference()).status()).isEqualTo(Booking.Status.CONFIRMED);
        assertThat(service.cancel(edge.reference()).status()).isEqualTo(Booking.Status.CANCELLED);
    }

    @Test
    @DisplayName("Unknown references give a clear 404")
    void unknownReference() {
        assertRule(() -> service.get("VH-NOPE22"), HttpStatus.NOT_FOUND, "VH-NOPE22");
        assertRule(() -> service.cancel("VH-NOPE22"), HttpStatus.NOT_FOUND, "VH-NOPE22");
    }

    @Test
    @DisplayName("Spam-bot enquiries (hidden field filled) are accepted quietly but never stored")
    void honeypotEnquiriesDropped() {
        EnquiryService inbox = new EnquiryService(clockAt(TUESDAY.atTime(8, 0)));
        inbox.receive("Ana", "ANA@example.com", "pricing", "How much is a colour?", "");
        inbox.receive("Bot", "bot@spam.test", "general", "Buy now", "http://spam.test");
        assertThat(inbox.inbox()).singleElement().satisfies(e -> {
            assertThat(e.name()).isEqualTo("Ana");
            assertThat(e.email()).isEqualTo("ana@example.com");
        });
    }
}
