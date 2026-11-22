package com.jdcreative.bookloom.booking;

import org.springframework.http.HttpStatus;

/** A booking rule was broken. Carries the HTTP status and a message written for the customer. */
public class BookingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;

    private BookingException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static BookingException notFound(String message) {
        return new BookingException(HttpStatus.NOT_FOUND, message);
    }

    /** The request is valid, but the slot or booking is not in a state that allows it. */
    public static BookingException conflict(String message) {
        return new BookingException(HttpStatus.CONFLICT, message);
    }

    /** The request is well formed but breaks a business rule (closed day, outside hours, too far ahead). */
    public static BookingException invalid(String message) {
        return new BookingException(HttpStatus.UNPROCESSABLE_CONTENT, message);
    }

    public HttpStatus status() {
        return status;
    }
}
