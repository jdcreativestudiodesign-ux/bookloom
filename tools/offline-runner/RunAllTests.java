package com.jdcreative.bookloom.tools;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

import java.io.PrintWriter;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * Runs every test without a build tool (used on build machines with no network).
 * Lives outside src/ on purpose: Gradle never compiles it. With Gradle, just use `gradle test`.
 */
public final class RunAllTests {

    private RunAllTests() {
    }

    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectPackage("com.jdcreative.bookloom"))
                .build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.execute(request, listener);
        TestExecutionSummary summary = listener.getSummary();
        summary.printFailuresTo(new PrintWriter(System.out), 15);
        System.out.printf("TESTS: %d found, %d passed, %d failed%n",
                summary.getTestsFoundCount(), summary.getTestsSucceededCount(), summary.getTotalFailureCount());
        System.exit(summary.getTotalFailureCount() == 0 && summary.getTestsFoundCount() > 0 ? 0 : 1);
    }
}
