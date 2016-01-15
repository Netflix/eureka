/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.discovery.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.BasicCounter;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.MonitorConfig;

/**
 * Counters for exceptions.
 *
 * @author Tomasz Bak
 */
public class ExceptionsMetric {

    private final String name;

    private final ConcurrentHashMap<String, Counter> exceptionCounters = new ConcurrentHashMap<>();

    public ExceptionsMetric(String name) {
        this.name = name;
    }

    public void count(Throwable ex) {
        getOrCreateCounter(extractName(ex)).increment();
    }

    public void shutdown() {
        ServoUtil.unregister(exceptionCounters.values());
    }

    private Counter getOrCreateCounter(String exceptionName) {
        Counter counter = exceptionCounters.get(exceptionName);
        if (counter == null) {
            counter = new BasicCounter(MonitorConfig.builder(name).withTag("id", exceptionName).build());
            if (exceptionCounters.putIfAbsent(exceptionName, counter) == null) {
                DefaultMonitorRegistry.getInstance().register(counter);
            } else {
                counter = exceptionCounters.get(exceptionName);
            }
        }
        return counter;
    }

    private static String extractName(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }
}
