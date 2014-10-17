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

package com.netflix.rx.eureka.utils;

import com.netflix.servo.monitor.LongGauge;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.monitor.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Nitesh Kant
 */
public final class ServoUtils {

    private static final Logger logger = LoggerFactory.getLogger(ServoUtils.class);

    private ServoUtils() {
    }

    public static <T> T registerObject(String id, T objectToRegister) {
        try {
            Monitors.registerObject(id, objectToRegister);
        } catch (Exception e) {
            logger.error("Failed to register object with the servo registry.", e);
        }
        return objectToRegister;
    }

    public static void unregisterObject(String id, Object objectToRegister) {
        try {
            Monitors.unregisterObject(id, objectToRegister);
        } catch (Exception e) {
            logger.error("Failed to unregister object with the servo registry.", e);
        }
    }

    public static LongGauge newLongGauge(String name) {
        return new LongGauge(MonitorConfig.builder(name).build());
    }

    public static long incrementLongGauge(LongGauge gauge) {
        return gauge.getNumber().incrementAndGet();
    }

    public static long decrementLongGauge(LongGauge gauge) {
        return gauge.getNumber().decrementAndGet();
    }
}
