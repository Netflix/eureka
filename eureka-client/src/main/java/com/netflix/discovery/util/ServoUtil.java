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

import java.util.Collection;

import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.Monitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public final class ServoUtil {

    private static final Logger logger = LoggerFactory.getLogger(ServoUtil.class);

    private ServoUtil() {
    }

    public static <T> boolean register(Monitor<T> monitor) {
        try {
            DefaultMonitorRegistry.getInstance().register(monitor);
        } catch (Exception e) {
            logger.warn("Cannot register monitor {}", monitor.getConfig().getName());
            if (logger.isDebugEnabled()) {
                logger.debug(e.getMessage(), e);
            }
            return false;
        }
        return true;
    }

    public static <T> void unregister(Monitor<T> monitor) {
        if (monitor != null) {
            try {
                DefaultMonitorRegistry.getInstance().unregister(monitor);
            } catch (Exception ignore) {
            }
        }
    }

    public static void unregister(Monitor... monitors) {
        for (Monitor monitor : monitors) {
            unregister(monitor);
        }
    }

    public static <M extends Monitor> void unregister(Collection<M> monitors) {
        for (M monitor : monitors) {
            unregister(monitor);
        }
    }
}
