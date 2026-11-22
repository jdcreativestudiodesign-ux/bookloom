# Case study: online booking for a small salon

*Portfolio sample. The scenario and salon are made up; everything listed under "What it does" and "Proof" is real and tested.*

**Example scenario.** A two-chair salon takes bookings by phone and Instagram messages, with a paper diary. Slots get double-booked, late cancellations leave gaps, and enquiries sent outside opening hours get missed.

**What I built.**
- A booking system (API) that knows the salon's rules: opening hours, appointment lengths, 15-minute start times, a 60-day booking window and a 24-hour cancellation policy.
- Customer-friendly error messages that a website can show as they are.
- A contact form with a hidden spam trap instead of an annoying CAPTCHA.

**What it does.**
- Shows only the times that are really free.
- Never double-books, even when two people book the same slot at the same moment.
- Frees a slot straight away when a booking is cancelled in time.
- Keeps a clean list of genuine enquiries.

**Proof.** 20 automated tests, including one that runs 400 simultaneous booking races. 8 deliberate bugs are planted by a script, and all 8 are caught. Verified on a Mac with Gradle 9.9.2 and Java 25 (Spring Boot 4.2.0).

**Stack.** Java 25, Spring Boot 4, JUnit. Comes with a README, an API table and test results.
