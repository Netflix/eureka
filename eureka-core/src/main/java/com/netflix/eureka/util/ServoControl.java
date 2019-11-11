package com.netflix.eureka.util;

import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.monitor.StatsTimer;
import com.netflix.servo.stats.StatsConfig;

/**
 * The sole purpose of this class is shutting down the {@code protected} executor of {@link StatsTimer}
 */
public class ServoControl extends StatsTimer {

    public ServoControl(MonitorConfig baseConfig, StatsConfig statsConfig) {
        super(baseConfig, statsConfig);
        throw new UnsupportedOperationException(getClass().getName() + " is not meant to be instantiated.");
    }

    public static void shutdown() {
        DEFAULT_EXECUTOR.shutdown();
    }

}
