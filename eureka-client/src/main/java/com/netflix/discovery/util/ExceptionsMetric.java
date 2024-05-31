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

import com.netflix.spectator.api.Counter;
import java.util.concurrent.ConcurrentHashMap;

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

    }

    private Counter getOrCreateCounter(String exceptionName) {
        Counter counter = exceptionCounters.get(exceptionName);
        if (counter != null) {
            return counter;
        }
        return exceptionCounters.computeIfAbsent(exceptionName, s ->
            SpectatorUtil.counter(name, exceptionName, ExceptionsMetric.class));
    }

    private static String extractName(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }
}
