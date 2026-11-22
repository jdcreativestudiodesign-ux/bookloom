package com.jdcreative.bookloom.booking;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Opening hours and booking limits, matching the Velloway Hair Studio website:
 * Tuesday to Friday 09:00 to 19:00, Saturday 09:00 to 16:00, closed Sunday and Monday.
 * Constants, so the tests pin them and a change is deliberate.
 */
@Component
public class BusinessHours {

    public static final LocalTime OPENS = LocalTime.of(9, 0);
    public static final LocalTime WEEKDAY_CLOSES = LocalTime.of(19, 0);
    public static final LocalTime SATURDAY_CLOSES = LocalTime.of(16, 0);
    public static final String CLOSED_DAYS_TEXT = "Sundays and Mondays";
    public static final int SLOT_MINUTES = 15;
    public static final int MAX_DAYS_AHEAD = 60;
    public static final Duration FREE_CANCELLATION_NOTICE = Duration.ofHours(24);

    /** Closing time on that day, or empty when the studio is closed all day. */
    public Optional<LocalTime> closesOn(DayOfWeek day) {
        return switch (day) {
            case TUESDAY, WEDNESDAY, THURSDAY, FRIDAY -> Optional.of(WEEKDAY_CLOSES);
            case SATURDAY -> Optional.of(SATURDAY_CLOSES);
            case SUNDAY, MONDAY -> Optional.empty();
        };
    }

    public boolean isOpenOn(DayOfWeek day) {
        return closesOn(day).isPresent();
    }

    public boolean isOnSlotBoundary(LocalTime time) {
        return time.getSecond() == 0 && time.getNano() == 0 && time.getMinute() % SLOT_MINUTES == 0;
    }
}
