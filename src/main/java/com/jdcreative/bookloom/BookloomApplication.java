package com.jdcreative.bookloom;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Bookloom: online booking and enquiry API for a small appointment-based business.
 * Portfolio sample by jdcreative.studiodesign. "Velloway Hair Studio" is a made-up business.
 */
@SpringBootApplication
public class BookloomApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookloomApplication.class, args);
    }

    /** One clock for the whole app, so tests can fix "now" and every rule uses the same time zone. */
    @Bean
    Clock clock(@Value("${bookloom.zone:America/New_York}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
