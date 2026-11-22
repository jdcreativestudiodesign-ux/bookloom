# Fix case study: "the tests pass here, but the build fails there"

*A real problem from building this sample, and how it was fixed and proven. No client was involved.*

**The problem.** Bookloom's 20 tests passed on the build machine, but the very first build on a Mac with Gradle (the standard Java build tool) failed before running a single test. This is a classic: code that works on one machine and breaks on another, usually found by a client or a deployment at the worst moment.

**The cause.** The build machine has no internet access, so it runs the tests with a small helper program instead of Gradle. That helper had been placed among the real tests (`src/test`). Gradle compiles everything in that folder, and the helper needs a library that Gradle only provides when tests *run*, not when they are *compiled*. On the build machine, that library was always present, so the mistake was invisible there.

**The fix, in two parts.**

1. **Fix the mistake:** the helper moved to `tools/offline-runner/`, outside the folders Gradle builds. The helper and the real tests now can't get mixed up again.
2. **Stop it coming back:** the offline test script now compiles the tests a second time with exactly the libraries Gradle would give them (a "parity check"), and stops if that fails. A mismatch now shows up on the build machine, before anything reaches a Mac or a server.

**The proof.**

- **The check catches the original mistake.** Putting the helper back in `src/test` makes the script stop with 13 compile errors, all in the misplaced file, and the message `STOP: tests would not compile under Gradle`.
- **The fixed project passes on both machines.** Build machine: 20 tests pass, 0 compiler warnings. Mac with Gradle 9.9.2 and Java 25: `BUILD SUCCESSFUL`, 20 tests run, 0 skipped, 0 failed.
- **It stays fixed.** Every push is now also built and tested by GitHub with Gradle, so a third machine checks it every time.

**What this shows a client.** When something breaks, the job is not done when it works again. It's done when the cause is understood, the fix is proven, and there's a check that stops the same problem coming back.
