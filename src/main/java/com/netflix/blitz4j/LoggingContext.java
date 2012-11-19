/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.blitz4j;

import java.util.concurrent.TimeUnit;

import org.apache.log4j.MDC;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;

import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;

/**
 * The utility class that caches the context of logging such as location
 * information.
 * 
 * <p>
 * It is expensive to find out the location information (ie) calling class, line
 * number etc of the logger and hence caching would be useful whenever possible.
 * This class also generates location information slightly more efficiently than
 * log4j.
 * <p>
 * 
 * @author Karthik Ranganathan
 * 
 */
public class LoggingContext {

    private static final BlitzConfig CONFIGURATION = LoggingConfiguration.getInstance().getConfiguration();
    private static final String LOCATION_INFO = "locationInfo";
    private ThreadLocal<StackTraceElement> stackLocal = new ThreadLocal<StackTraceElement>();
    private ThreadLocal<LoggingEvent> loggingEvent = new ThreadLocal<LoggingEvent>();

    private static final LoggingContext instance = new LoggingContext();
    private Timer stackTraceTimer = Monitors.newTimer("getStacktraceElement",
            TimeUnit.NANOSECONDS);

    private LoggingContext() {
        try {
            Monitors.registerObject(this);
        } catch (Throwable e) {
            if (CONFIGURATION.shouldPrintLoggingErrors()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Gets the starting calling stack trace element of a given stack which
     * matches the given class name. Given the wrapper class name, the match
     * continues until the last stack trace element of the wrapper class is
     * matched.
     * 
     * @param stackClass
     *            - The class to be matched for. Get the last matching class
     *            down the stack
     * @return - StackTraceElement which denotes the calling point of given
     *         class or wrapper class
     */
    public StackTraceElement getStackTraceElement(Class stackClass) {

        Stopwatch s = stackTraceTimer.start();
        Throwable t = new Throwable();
        StackTraceElement[] stArray = t.getStackTrace();
        int stackSize = stArray.length;
        StackTraceElement st = null;
        for (int i = 0; i < stackSize; i++) {
            boolean found = false;
            while (stArray[i].getClassName().equals(stackClass.getName())) {
                ++i;
                found = true;
            }
            if (found) {
                st = stArray[i];
            }
        }

        s.stop();

        return st;
    }

    /**
     * Get the location information of the calling class
     * 
     * @param wrapperClassName
     *            - The wrapper that indicates the caller
     * @return the location information
     */
    public LocationInfo getLocationInfo(Class wrapperClassName) {
        LocationInfo locationInfo = null;

        try {
            if (stackLocal.get() == null) {
                stackLocal.set(this.getStackTraceElement(wrapperClassName));
            }

            locationInfo = new LocationInfo(stackLocal.get().getFileName(),
                    stackLocal.get().getClassName(), stackLocal.get()
                            .getMethodName(), stackLocal.get().getLineNumber()
                            + "");
        } catch (Throwable e) {
            if (CONFIGURATION
                    .shouldPrintLoggingErrors()) {
                e.printStackTrace();
            }
        }
        return locationInfo;
    }

    /**
     * Clears any logging information that was cached for the purpose of
     * logging.
     */
    private void clearLocationInfo() {
        MDC.remove(LOCATION_INFO);
        stackLocal.set(null);
    }

    public static LoggingContext getInstance() {
        return instance;
    }

    /**
     * Generate the location information of the given logging event and cache
     * it.
     * 
     * @param event
     *            The logging event for which the location information needs to
     *            be determined.
     * @return The location info object contains information about the logger.
     */
    public LocationInfo generateLocationInfo(LoggingEvent event) {
        // If the event is not the same, clear the cache
        if (event != loggingEvent.get()) {
            loggingEvent.set(event);
            clearLocationInfo();
        }
        LocationInfo locationInfo = null;
        try {
            locationInfo = (LocationInfo) LoggingContext
                    .getInstance()
                    .getLocationInfo(Class.forName(event.getFQNOfLoggerClass()));
            if (locationInfo != null) {
                MDC.put(LOCATION_INFO, locationInfo);
            }
        } catch (Throwable e) {
            if (CONFIGURATION
                    .shouldPrintLoggingErrors()) {
                e.printStackTrace();
            }
        }
        return locationInfo;
    }

    /**
     * Get the location information of the logging event. If the information has
     * been cached it is retrieved from the MDC (for asynchronous events MDCs
     * are retained), else it is generated.
     * 
     * @param event
     *            - The logging event
     * @return- The location information of the logging event.
     */
    public LocationInfo getLocationInfo(LoggingEvent event) {
        if (event != loggingEvent.get()) {
            loggingEvent.set(event);
            clearLocationInfo();
        }
        // For async appenders, the locationInfo is set in the MDC and not with
        // the thread since the thread that processes the logging is different
        // from the one that
        // generates location information.
        LocationInfo locationInfo = (LocationInfo) event.getMDC(LOCATION_INFO);
        if (locationInfo == null) {
            locationInfo = this.generateLocationInfo(event);
        }

        return locationInfo;
    }

}
