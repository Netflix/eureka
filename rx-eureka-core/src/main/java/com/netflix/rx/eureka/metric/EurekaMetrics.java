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

package com.netflix.rx.eureka.metric;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.rx.eureka.utils.ServoUtils;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.LongGauge;
import com.netflix.servo.monitor.Monitor;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for component specific metric implementations.
 *
 * @author Tomasz Bak
 */
public abstract class EurekaMetrics {

    private static final Logger logger = LoggerFactory.getLogger(EurekaMetrics.class);

    enum STATE {Init, Up, Down}

    private final String rootName;

    private final AtomicReference<STATE> state = new AtomicReference<>(STATE.Init);
    private final List<Monitor<?>> monitors = new ArrayList<>();
    private final List<EurekaMetrics> nestedMetrics = new ArrayList<>();

    protected EurekaMetrics(String rootName) {
        this.rootName = rootName;
    }

    @PostConstruct
    public void bindMetrics() {
        if (state.compareAndSet(STATE.Init, STATE.Up)) {
            ServoUtils.registerObject(rootName, this);
            for (Monitor<?> m : monitors) {
                DefaultMonitorRegistry.getInstance().register(m);
            }
            for (EurekaMetrics e : nestedMetrics) {
                e.bindMetrics();
            }
        }
    }

    @PreDestroy
    public void unbindMetrics() {
        if (state.compareAndSet(STATE.Up, STATE.Down)) {
            ServoUtils.unregisterObject(rootName, this);
            for (Monitor<?> m : monitors) {
                DefaultMonitorRegistry.getInstance().unregister(m);
            }
            for (EurekaMetrics e : nestedMetrics) {
                e.unbindMetrics();
            }
        }
    }

    protected String fullName(String baseName) {
        return rootName + '.' + baseName;
    }

    protected Counter newCounter(String baseName) {
        return Monitors.newCounter(fullName(baseName));
    }

    protected LongGauge newLongGauge(String baseName) {
        return ServoUtils.newLongGauge(fullName(baseName));
    }

    protected Timer newTimer(String baseName) {
        return Monitors.newTimer(fullName(baseName));
    }

    protected void register(Monitor<?>... additionalMonitors) {
        Collections.addAll(monitors, additionalMonitors);
        if (state.get() == STATE.Up) {
            for (Monitor<?> m : additionalMonitors) {
                DefaultMonitorRegistry.getInstance().register(m);
            }
        }
    }

    protected void register(EurekaMetrics... additionalMetrics) {
        Collections.addAll(nestedMetrics, additionalMetrics);
        if (state.get() == STATE.Up) {
            for (EurekaMetrics e : additionalMetrics) {
                e.bindMetrics();
            }
        }
    }
}
