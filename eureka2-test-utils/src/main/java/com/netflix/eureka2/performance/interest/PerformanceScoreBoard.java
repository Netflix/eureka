package com.netflix.eureka2.performance.interest;

import java.io.PrintStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Tomasz Bak
 */
public class PerformanceScoreBoard {

    private final AtomicInteger registrySize = new AtomicInteger();
    private final AtomicInteger processedRegistrations = new AtomicInteger();
    private final AtomicInteger processedUnregistrations = new AtomicInteger();

    private final AtomicInteger processedInterests = new AtomicInteger();
    private final AtomicInteger activeInterests = new AtomicInteger();
    private final AtomicInteger processedNotifications = new AtomicInteger();

    public void setRegistrySize(int registrySize) {
        this.registrySize.set(registrySize);
    }

    public void processedRegistrationIncrement() {
        processedRegistrations.incrementAndGet();
    }

    public void processedUnregistrationIncrement() {
        processedUnregistrations.incrementAndGet();
    }

    public void processedInterestsIncrement() {
        processedInterests.incrementAndGet();
    }

    public void activeInterestsIncrement() {
        activeInterests.incrementAndGet();
    }

    public void activeInterestsDecrement() {
        activeInterests.decrementAndGet();
    }

    public void processedNotificationsIncrement() {
        processedNotifications.incrementAndGet();
    }

    public void renderScoreBoard(PrintStream out) {
        out.println("###################################################################");
        out.println(" Time: " + DateFormat.getTimeInstance().format(new Date()));
        out.println(" registry size               :     " + registrySize);
        out.println(" processed registrations     :     " + processedRegistrations);
        out.println(" processed unregistrations   :     " + processedUnregistrations);
        out.println(" processed interests         :     " + processedInterests);
        out.println(" active interests            :     " + activeInterests);
        out.println(" processed notifications     :     " + processedNotifications);
        out.println();
    }
}
