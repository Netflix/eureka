/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka2.metric.client;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.netflix.eureka2.metric.SpectatorEurekaMetrics;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.ValueFunction;

/**
 * @author Tomasz Bak
 */
public class SpectatorEurekaClientConnectionMetrics extends SpectatorEurekaMetrics {

    private final AtomicLong clientConnections = new AtomicLong();
    private final Timer connectionTime;
    private final Timer ackWaitTime;

    /**
     * We could use {@link java.util.concurrent.ConcurrentHashMap} with putIfAbsent method
     * to eliminate locking, if creating multiple instances of the same counter wouldn't be
     * a problem.
     */
    private final Map<String, Counter> messageCounters = new HashMap<>();
    private final Counter totalIncomingMessages;
    private final Counter totalOutgoingMessages;

    public SpectatorEurekaClientConnectionMetrics(ExtendedRegistry registry, String rootName) {
        super(registry, rootName);
        newLongGauge("clientConnections", new ValueFunction() {
            @Override
            public double apply(Object ref) {
                return clientConnections.get();
            }
        });
        this.connectionTime = newTimer("connectionTime");
        this.ackWaitTime = newTimer("ackWaitTime");
        this.totalIncomingMessages = newCounter("incoming.total");
        this.totalOutgoingMessages = newCounter("outgoing.total");
    }

    public void clientConnectionTime(long start) {
        connectionTime.record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
    }

    public void incrementIncomingMessageCounter(Class<?> aClass, int amount) {
        Counter counter = getMessageCounter("incoming." + aClass.getSimpleName());
        counter.increment(amount);
        totalIncomingMessages.increment(amount);
    }

    public void incrementOutgoingMessageCounter(Class<?> aClass, int amount) {
        Counter counter = getMessageCounter("outgoing." + aClass.getSimpleName());
        counter.increment(amount);
        totalOutgoingMessages.increment(amount);
    }

    private Counter getMessageCounter(String counterName) {
        Counter counter = messageCounters.get(counterName);
        if (counter == null) {
            synchronized (messageCounters) {
                counter = messageCounters.get(counterName);
                if (counter == null) {
                    counter = newCounter(counterName);
                    messageCounters.put(counterName, counter);
                }
            }
        }
        return counter;
    }

    public void ackWaitTime(long startTime) {
        ackWaitTime.record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
    }

    public void incrementClientConnections() {
        clientConnections.incrementAndGet();
    }

    public void decrementClientConnections() {
        clientConnections.incrementAndGet();
    }
}
