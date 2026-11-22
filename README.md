# Bookloom: online booking and enquiry API

[![tests](https://github.com/jdcreativestudiodesign-ux/bookloom/actions/workflows/test.yml/badge.svg)](https://github.com/jdcreativestudiodesign-ux/bookloom/actions/workflows/test.yml)

> **Portfolio sample** by **jdcreative.studiodesign**. "Velloway Hair Studio" is a made-up salon used for the demo. It's the same salon as the [live landing-page sample](https://velloway-salon.vercel.app), with the same hours, menu and prices, so the two samples show the page and the booking system behind it.

Bookloom is the back end behind a "Book now" button and a contact form for an appointment-based business: a salon, a clinic, a tutor or a repair shop. It shows which times are free, takes bookings without ever double-booking, lets customers cancel online, and receives enquiries with a built-in spam filter.

**Proof:** 20 automated tests, and 8 of 8 deliberately planted bugs caught. Every push is tested on GitHub with Java 25.

## Run it

Requires Java 25 or newer.

```
./gradlew bootRun     # starts on http://localhost:8080
./gradlew test        # runs all 20 tests
```

## Real responses

These were captured from the running app.

```
$ curl localhost:8080/api/services
[{"id":"cut","name":"Cut and finish","durationMinutes":60,"price":55.00,"priceFrom":false},
 {"id":"colour","name":"Colour","durationMinutes":90,"price":95.00,"priceFrom":true}, ...]

$ curl 'localhost:8080/api/availability?serviceId=cut&date=2026-11-25'
{"slots":["09:00","09:15","09:30", ... ,"17:45","18:00"],"serviceId":"cut","date":"2026-11-25"}

$ curl -X POST localhost:8080/api/bookings -H 'Content-Type: application/json' \
    -d '{"serviceId":"cut","name":"Sam Rivera","email":"sam@example.com","start":"2026-11-25T10:00"}'
{
    "reference": "VH-5HYSGJ",
    "serviceId": "cut",
    "customerName": "Sam Rivera",
    "email": "sam@example.com",
    "start": "2026-11-25T10:00:00",
    "end": "2026-11-25T11:00:00",
    "status": "CONFIRMED"
}

# Someone else tries an overlapping time:
{
    "detail": "Sorry, that time was just taken. Please choose another slot.",
    "instance": "/api/bookings",
    "status": 409,
    "title": "Conflict"
}

# Someone tries to book on a Monday:
{
    "detail": "We're closed on Sundays and Mondays.",
    "instance": "/api/bookings",
    "status": 422,
    "title": "Unprocessable Content"
}

# A form submitted with mistakes:
{
    "detail": "Some details need fixing.",
    "status": 400,
    "fields": {
        "name": "Please enter your name.",
        "email": "Please enter a valid email address.",
        "start": "Please choose a date and time."
    }
}
```

## The problem it solves

Small businesses often take bookings by phone, DMs and a paper diary. Slots get double-booked, people cancel at the last minute, and enquiries get lost. Bookloom applies the business's own rules automatically, so the website can offer real-time booking and the owner only steps in when needed.

## What it does

- **Live availability:** only times where the whole appointment fits before that day's closing time, never in the past, never on a closed day.
- **No double booking**, even when several people press "Book" at the same moment (tested with 400 simultaneous races).
- **Clear rules:** open Tuesday to Friday 09:00 to 19:00 and Saturday 09:00 to 16:00, closed Sunday and Monday, 15-minute slots, bookings up to 60 days ahead, in the salon's own time zone (New York). All of these are easy to change.
- **Online cancellation** up to 24 hours before. After that, the customer is asked to call.
- **Readable booking references** such as `VH-5HYSGJ`, with no 0/O or 1/I/L to mix up on the phone.
- **Contact form** with friendly validation and a hidden spam trap that bots fill in and people never see.
- **Friendly errors:** every error has the same shape, with a message the website can show as-is. Stack traces never leave the server.

## API

| Method | Path | What it does |
|---|---|---|
| `GET` | `/api/services` | The menu: name, length, price |
| `GET` | `/api/availability?serviceId=cut&date=2026-12-02` | Free start times that day |
| `POST` | `/api/bookings` | Book (`serviceId`, `name`, `email`, `start`) |
| `GET` | `/api/bookings/{reference}` | Look up a booking |
| `DELETE` | `/api/bookings/{reference}` | Cancel (24 hours' notice) |
| `POST` | `/api/enquiries` | Contact form (`name`, `email`, `topic`, `message`) |

Status codes: `201` created · `400` fix the form · `404` not found · `409` slot taken or too late to cancel · `422` breaks a business rule, such as a closed day.

## Tests

**20 tests, all passing:** 13 for the booking rules and 7 that drive the whole app over HTTP. "Now" is fixed in the tests, so they give the same result on any day.

To prove the tests catch real mistakes, `negative-controls.sh` plants 8 deliberate bugs, one at a time, in a throwaway copy, and checks that the tests fail. Result: **8 of 8 caught.** The planted bugs are: a broken overlap check, an off-by-one at closing time, a widened cancellation window, an inverted spam filter, a missing email check, a removed booking lock, Saturday treated as a full-length day, and bookings allowed on a closed Monday.

**Build-machine helpers (not needed to run the app):** `run-tests-offline.sh` and `tools/offline-runner/` run the same tests without Gradle, on a machine with no internet access. `negative-controls.sh` uses them. Both take the path to a folder of library jars. Why they exist, and the build check they led to, is told in [FIX-CASE-STUDY.md](FIX-CASE-STUDY.md).

## Stack

Java 25 · Spring Boot 4.2 · Spring MVC · Jakarta Validation · JUnit 6 · AssertJ · Gradle

## Easy next steps for a real client

A database (PostgreSQL) instead of in-memory storage · email or SMS confirmations · several staff members with their own diaries · deposits through Stripe · an admin page for the owner.

## Licence

© 2026 jdcreative.studiodesign. All rights reserved. Shared for portfolio viewing only; see [LICENSE](LICENSE).
