package com.jdcreative.bookloom;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The whole app over HTTP, with "now" fixed at Tuesday 1 Dec 2026, 08:00 New York time. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BookloomApiTest {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            ZoneId newYork = ZoneId.of("America/New_York");
            return Clock.fixed(ZonedDateTime.of(LocalDateTime.of(2026, 12, 1, 8, 0), newYork).toInstant(), newYork);
        }
    }

    @Autowired
    MockMvcTester mvc;

    MvcTestResult postJson(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    static String bookingJson(String serviceId, String start) {
        return """
                {"serviceId":"%s","name":"Sam Rivera","email":"sam@example.com","start":"%s"}
                """.formatted(serviceId, start);
    }

    @Test
    @DisplayName("GET /api/services lists the menu with prices")
    void listsServices() {
        assertThat(mvc.get().uri("/api/services")).hasStatusOk()
                .bodyJson().extractingPath("$[*].id").asArray().containsExactly("cut", "colour", "blow-dry", "treatment");
        assertThat(mvc.get().uri("/api/services")).bodyJson().extractingPath("$[1].priceFrom").isEqualTo(true);
    }

    @Test
    @DisplayName("Book, look up, then cancel: the full happy path")
    void bookLookUpCancel() {
        MvcTestResult created = postJson("/api/bookings", bookingJson("cut", "2026-12-02T10:00"));
        assertThat(created).hasStatus(201).hasHeader("Location", "/api/bookings/" + reference(created));
        assertThat(created).bodyJson().extractingPath("$.end").isEqualTo("2026-12-02T11:00:00");

        String ref = reference(created);
        assertThat(mvc.get().uri("/api/bookings/{ref}", ref)).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("CONFIRMED");
        assertThat(mvc.get().uri("/api/availability?serviceId=cut&date=2026-12-02")).hasStatusOk()
                .bodyJson().extractingPath("$.slots").asArray().doesNotContain("10:00").contains("11:00");

        assertThat(mvc.delete().uri("/api/bookings/{ref}", ref)).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
        assertThat(mvc.get().uri("/api/availability?serviceId=cut&date=2026-12-02"))
                .bodyJson().extractingPath("$.slots").asArray().contains("10:00");
    }

    @Test
    @DisplayName("A taken slot returns 409 with a message the website can show as-is")
    void conflictIsFriendly() {
        postJson("/api/bookings", bookingJson("cut", "2026-12-02T10:00"));
        MvcTestResult clash = postJson("/api/bookings", bookingJson("treatment", "2026-12-02T10:30"));
        assertThat(clash).hasStatus(409).bodyJson().extractingPath("$.detail")
                .isEqualTo("Sorry, that time was just taken. Please choose another slot.");
    }

    @Test
    @DisplayName("Broken business rules return 422; unknown service or reference returns 404")
    void rulesAndNotFound() {
        assertThat(postJson("/api/bookings", bookingJson("cut", "2026-12-06T10:00"))).hasStatus(422)
                .bodyJson().extractingPath("$.detail").asString().contains("closed on Sundays and Mondays");
        assertThat(postJson("/api/bookings", bookingJson("massage", "2026-12-02T10:00"))).hasStatus(404);
        assertThat(mvc.get().uri("/api/bookings/VH-NOPE22")).hasStatus(404);
    }

    @Test
    @DisplayName("Missing or invalid fields return 400 with one message per field")
    void validationErrors() {
        MvcTestResult bad = postJson("/api/bookings", """
                {"serviceId":"cut","name":"","email":"not-an-email"}
                """);
        assertThat(bad).hasStatus(400).bodyJson().extractingPath("$.fields").asMap()
                .containsEntry("name", "Please enter your name.")
                .containsEntry("email", "Please enter a valid email address.")
                .containsEntry("start", "Please choose a date and time.");
    }

    @Test
    @DisplayName("Unreadable JSON or a bad date returns 400 with a hint, never a stack trace")
    void unreadableRequests() throws Exception {
        MvcTestResult garbled = postJson("/api/bookings", bookingJson("cut", "next tuesday"));
        assertThat(garbled).hasStatus(400).bodyJson().extractingPath("$.detail").asString().contains("2026-12-01T10:30");
        assertThat(garbled.getResponse().getContentAsString()).doesNotContain("Exception", "at com.");
        assertThat(mvc.get().uri("/api/availability?serviceId=cut&date=soon")).hasStatus(400);
        assertThat(mvc.get().uri("/api/availability?date=2026-12-02")).hasStatus(400);
    }

    @Test
    @DisplayName("Enquiries: 201 with a friendly reply; topic is checked")
    void enquiries() {
        assertThat(postJson("/api/enquiries", """
                {"name":"Ana","email":"ana@example.com","topic":"pricing","message":"How much is a colour?"}
                """)).hasStatus(201).bodyJson().extractingPath("$.message").asString().startsWith("Thanks!");
        assertThat(postJson("/api/enquiries", """
                {"name":"Ana","email":"ana@example.com","topic":"crypto","message":"Hi"}
                """)).hasStatus(400).bodyJson().extractingPath("$.fields.topic").isEqualTo("Please pick a topic from the list.");
    }

    private static String reference(MvcTestResult result) {
        try {
            String body = result.getResponse().getContentAsString();
            return com.jayway.jsonpath.JsonPath.read(body, "$.reference");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
