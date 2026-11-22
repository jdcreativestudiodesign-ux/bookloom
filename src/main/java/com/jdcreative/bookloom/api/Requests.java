package com.jdcreative.bookloom.api;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies, with validation messages written for customers rather than developers. */
public final class Requests {

    private Requests() {
    }

    public record BookingRequest(
            @NotBlank(message = "Please choose a service.") String serviceId,
            @NotBlank(message = "Please enter your name.") @Size(max = 80, message = "Name must be 80 characters or fewer.") String name,
            @NotBlank(message = "Please enter your email.") @Email(message = "Please enter a valid email address.") String email,
            @NotNull(message = "Please choose a date and time.") LocalDateTime start) {
    }

    public record EnquiryRequest(
            @NotBlank(message = "Please enter your name.") @Size(max = 80, message = "Name must be 80 characters or fewer.") String name,
            @NotBlank(message = "Please enter your email.") @Email(message = "Please enter a valid email address.") String email,
            @Pattern(regexp = "general|booking|pricing|other", message = "Please pick a topic from the list.") String topic,
            @NotBlank(message = "Please write a short message.") @Size(max = 2000, message = "Messages must be 2000 characters or fewer.") String message,
            String website) {

        public String topicOrDefault() {
            return topic == null ? "general" : topic;
        }
    }
}
