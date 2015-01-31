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

package com.netflix.eureka2.metric;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Timer;


/**
 * Metrics class for instances of MessageConnection(s).
 *
 * @author Tomasz Bak
 */
public class SpectatorMessageConnectionMetrics extends SpectatorEurekaMetrics implements MessageConnectionMetrics {

    private final AtomicLong connectedClients = new AtomicLong();
    private final Timer connectionTime;

    /**
     * We could use {@link java.util.concurrent.ConcurrentHashMap} with putIfAbsent method
     * to eliminate locking, if creating multiple instances of the same counter wouldn't be
     * a problem.
     */
    private final Map<String, Counter> messageCounters = new HashMap<>();
    private final Counter totalIncomingMessages;
    private final Counter totalOutgoingMessages;

    public SpectatorMessageConnectionMetrics(ExtendedRegistry registry, String context) {
        super(registry, context);
        newGauge("connectedClients", connectedClients);
        this.connectionTime = newTimer("connectionTime");
        this.totalIncomingMessages = newCounter("incoming.total");
        this.totalOutgoingMessages = newCounter("outgoing.total");
    }

    @Override
    public void incrementConnectionCounter() {
        connectedClients.incrementAndGet();
    }

    @Override
    public void decrementConnectionCounter() {
        connectedClients.decrementAndGet();
    }

    @Override
    public void connectionDuration(long start) {
        connectionTime.record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
    }

    @Override
    public void incrementIncomingMessageCounter(Class<?> aClass, int amount) {
        Counter counter = getMessageCounter("incoming." + aClass.getSimpleName());
        counter.increment(amount);
        totalIncomingMessages.increment(amount);
    }

    @Override
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
}
