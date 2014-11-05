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

package com.netflix.rx.eureka.transport.base;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.netflix.rx.eureka.metric.EurekaMetrics;
import com.netflix.rx.eureka.transport.MessageConnection;
import com.netflix.rx.eureka.utils.ServoUtils;
import com.netflix.servo.monitor.BasicCounter;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.LongGauge;
import com.netflix.servo.monitor.Timer;

/**
 * Metrics class for instances of {@link MessageConnection}s.
 *
 * @author Tomasz Bak
 */
public class MessageConnectionMetrics extends EurekaMetrics {

    private final LongGauge connectedClients;
    private final Timer connectionTime;

    /**
     * We could use {@link java.util.concurrent.ConcurrentHashMap} with putIfAbsent method
     * to eliminate locking, if creating multiple instances of the same counter wouldn't be
     * a problem.
     */
    private final Map<String, Counter> messageCounters = new HashMap<>();
    private final Counter totalIncomingMessages;
    private final Counter totalOutgoingMessages;

    public MessageConnectionMetrics(String context) {
        super(context);
        this.connectedClients = newLongGauge("connectedClients");
        this.connectionTime = newTimer("connectionTime");
        this.totalIncomingMessages = newCounter("incoming.total");
        this.totalOutgoingMessages = newCounter("outgoing.total");
    }

    public void incrementConnectedClients() {
        ServoUtils.incrementLongGauge(connectedClients);
    }

    public void decrementConnectedClients() {
        ServoUtils.decrementLongGauge(connectedClients);
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
                    counter = new BasicCounter(monitorConfig(counterName));
                    messageCounters.put(counterName, counter);
                    register(counter);
                }
            }
        }
        return counter;
    }
}
