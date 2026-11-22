#!/usr/bin/env bash
# Proves the tests catch real mistakes: plants one deliberate bug at a time in a throwaway copy
# of the code and checks the test run FAILS. Your working copy is never touched.
# Usage: ./negative-controls.sh /path/to/deps
set -uo pipefail
DEPS="${1:?give the deps folder}"
HERE="$(cd "$(dirname "$0")" && pwd)"
B=src/main/java/com/jdcreative/bookloom

# file | text to find | bug to plant | what it breaks
CONTROLS=(
  "$B/booking/Booking.java|start.isBefore(otherEnd)|start.isAfter(otherEnd)|overlap check lets double bookings through"
  "$B/booking/BookingService.java|<= closes.toSecondOfDay()|< closes.toSecondOfDay()|appointment ending exactly at closing refused"
  "$B/booking/BookingService.java|.isAfter(booking.start())|.isAfter(booking.start().plusDays(2))|24-hour cancellation window widened"
  "$B/enquiry/EnquiryService.java|!honeypot.isBlank()|honeypot.isBlank()|spam filter drops real enquiries"
  "$B/api/Requests.java|@Email(message = \"Please enter a valid email address.\") String email,|String email,|booking form accepts a bad email address"
  "$B/booking/BookingService.java|public synchronized Booking book|public Booking book|double booking under load"
  "$B/booking/BusinessHours.java|case SATURDAY -> Optional.of(SATURDAY_CLOSES);|case SATURDAY -> Optional.of(WEEKDAY_CLOSES);|Saturday treated as a full 19:00 day"
  "$B/booking/BusinessHours.java|case SUNDAY, MONDAY -> Optional.empty();|case SUNDAY -> Optional.empty(); case MONDAY -> Optional.of(WEEKDAY_CLOSES);|bookings taken on Mondays when the studio is closed"
)

pass=0; fail=0; broken=0

# Baseline: the unchanged project must pass, or every "caught" below would be meaningless.
tmp="$(mktemp -d)"; cp -r "$HERE/." "$tmp/"
if ! "$tmp/run-tests-offline.sh" "$DEPS" > "$tmp/out.log" 2>&1; then
  echo "STOP: the unchanged project does not pass its tests:"; tail -10 "$tmp/out.log"; exit 1
fi
echo "BASELINE  unchanged project passes ($(grep -o 'TESTS:.*' "$tmp/out.log"))"
rm -rf "$tmp"

for c in "${CONTROLS[@]}"; do
  IFS='|' read -r file find plant what <<< "$c"
  tmp="$(mktemp -d)"; cp -r "$HERE/." "$tmp/"
  if ! grep -qF "$find" "$tmp/$file"; then echo "STOP: control text not found in $file: $find"; exit 1; fi
  python3 - "$tmp/$file" "$find" "$plant" <<'PY'
import sys; p, a, b = sys.argv[1:]; s = open(p).read(); open(p, "w").write(s.replace(a, b, 1))
PY
  if "$tmp/run-tests-offline.sh" "$DEPS" > "$tmp/out.log" 2>&1; then
    echo "SURVIVED  $what"; fail=$((fail+1))
  elif grep -qE 'TESTS: .* [1-9][0-9]* failed' "$tmp/out.log"; then
    echo "CAUGHT    $what  ($(grep -o 'TESTS:.*' "$tmp/out.log"))"; pass=$((pass+1))
  else
    # The run failed without any test failing (a compile error, missing libraries): proves nothing.
    echo "BROKEN    $what  (the test run did not complete; last lines below)"; tail -5 "$tmp/out.log"; broken=$((broken+1))
  fi
  rm -rf "$tmp"
done
echo "NEGATIVE CONTROLS: $pass caught, $fail survived, $broken broken"
[ "$fail" -eq 0 ] && [ "$broken" -eq 0 ]
