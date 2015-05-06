package com.netflix.eureka2.performance;

import java.io.PrintStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;

/**
 * @author Tomasz Bak
 */
public class PerformanceScoreBoard {

    private final AtomicInteger activeRegisteringActors = new AtomicInteger();
    private final AtomicInteger activeSubscribingActors = new AtomicInteger();

    private final HdrHistogramMetric notificationLatencyHdrHistogram = new HdrHistogramMetric();

    private final RateCounter registrationRate = new RateCounter();
    private final RateCounter notificationRate = new RateCounter();
    private final AtomicInteger latentNotifications = new AtomicInteger();

    public void registeringActorIncrement() {
        activeRegisteringActors.incrementAndGet();
        registrationRate.increment();
    }

    public void registeringActorDecrement() {
        activeRegisteringActors.decrementAndGet();
    }

    public void subscribingActorIncrement() {
        activeSubscribingActors.incrementAndGet();
    }

    public void subscribingActorDecrement() {
        activeSubscribingActors.decrementAndGet();
    }

    public void notificationIncrement() {
        notificationRate.increment();
    }

    public void latentNotificationIncrement() {
        latentNotifications.incrementAndGet();
    }

    public void recordNotificationLatency(long latency) {
        notificationLatencyHdrHistogram.recordValue(latency);
    }

    public void renderScoreBoard(PrintStream out) {
        out.println("###################################################################");
        out.println(" Time: " + DateFormat.getTimeInstance().format(new Date()));
        out.println(" active registering actors        :     " + activeRegisteringActors);
        out.println(" registration rate                :     " + registrationRate);
        out.println(" active subscribing actors        :     " + activeSubscribingActors);
        out.println(" notification rate                :     " + notificationRate);
        out.println(" latent notification              :     " + latentNotifications);
        out.println(" notification latency ([ms])      :");
        Map<Double, Long> latencyMap = notificationLatencyHdrHistogram.latestValue();
        out.print("        ");
        for (double percentile : latencyMap.keySet()) {
            out.printf("  | %6.2f  ", percentile);
        }
        out.println();
        out.print("        ");
        for (double percentile : latencyMap.keySet()) {
            out.printf("  | %6d  ", latencyMap.get(percentile));
        }
        out.println();
        out.println();
    }

    static class RateCounter {
        private long swapTime = System.currentTimeMillis();

        private int current;
        private int next;

        void increment() {
            long currentTime = System.currentTimeMillis();
            if (currentTime >= swapTime) {
                current = next;
                next = 0;
                swapTime = currentTime + 1000;
            }
            next++;
        }

        int getCurrent() {
            return current;
        }

        @Override
        public String toString() {
            return current + " [req/sec]";
        }
    }

    static class HdrHistogramMetric {

        private static final double[] PERCENTILES = {0, 25, 50, 60, 70, 80, 90, 95, 99.5, 99.95, 100};

        private final Histogram histogram = new ConcurrentHistogram(3);

        void recordValue(long value) {
            histogram.recordValue(value);
        }

        Map<Double, Long> latestValue() {
            Map<Double, Long> result = new TreeMap<>();
            for (double percentile : PERCENTILES) {
                result.put(percentile, histogram.getValueAtPercentile(percentile));
            }
            return result;
        }
    }

}
