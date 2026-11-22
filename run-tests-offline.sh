#!/usr/bin/env bash
# Compiles and runs every test using a folder of library jars instead of Gradle.
# Usage: ./run-tests-offline.sh /path/to/deps   (the folder holding main/ and test/ jar folders)
#
# Our own code must compile with zero warnings. JUnit's jars reference an optional annotation
# library (apiguardian) that a runtime-only jar set doesn't include; javac warns about that while
# reading JUnit's classes. Those library warnings are ignored here, but any warning that points at
# our own sources still fails the run. With Gradle (`gradle test`) none of this applies.
set -euo pipefail
DEPS="${1:?give the deps folder, e.g. ./run-tests-offline.sh ~/deps}"
OUT="$(mktemp -d)"
cd "$(dirname "$0")"

# 1. Main code: zero warnings allowed.
javac --release 25 -parameters -Xlint:all -Werror -d "$OUT/main" -cp "$DEPS/main/*" $(find src/main/java -name '*.java')
cp -r src/main/resources/. "$OUT/main/"

# 2. Gradle parity: compile the tests with only what Gradle puts on the test COMPILE classpath.
#    junit-platform-launcher is testRuntimeOnly in build.gradle, so it is left out here.
GRADLE_TEST_CP="$(ls "$DEPS"/test/*.jar | grep -v 'junit-platform-launcher' | tr '\n' ':')"
if ! javac -parameters -Xlint:all -Xmaxwarns 10000 -d "$OUT/parity" -cp "$OUT/main:$GRADLE_TEST_CP" \
        $(find src/test/java -name '*.java') 2> "$OUT/parity.log"; then
    grep -A3 "error" "$OUT/parity.log" | head -20; echo "STOP: tests would not compile under Gradle"; exit 1
fi

# 3. Tests plus the offline runner (kept outside src/ so Gradle never sees it).
if ! javac -parameters -Xlint:all -Xmaxwarns 10000 -d "$OUT/test" -cp "$OUT/main:$DEPS/test/*" \
        $(find src/test/java tools/offline-runner -name '*.java') 2> "$OUT/test-compile.log"; then
    cat "$OUT/test-compile.log"; echo "STOP: test compile failed"; exit 1
fi
if grep -qE "^(src|tools)/" "$OUT/test-compile.log" "$OUT/parity.log"; then
    grep -hE -A3 "^(src|tools)/" "$OUT/test-compile.log" "$OUT/parity.log"; echo "STOP: warnings in our own code"; exit 1
fi

# 4. Run. Mockito as a startup agent (its recommendation for Java 21+); -Xshare:off silences the
#    informational class-sharing note that the agent causes.
java -Xshare:off -javaagent:"$(ls "$DEPS"/test/mockito-core-*.jar)" \
    -cp "$OUT/test:$OUT/main:$DEPS/test/*" com.jdcreative.bookloom.tools.RunAllTests
