package com.netflix.blitz4j;

import java.util.concurrent.TimeUnit;

import org.apache.log4j.MDC;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;

import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;

public class LoggingContext {

    private ThreadLocal<StackTraceElement> stackLocal = new ThreadLocal<StackTraceElement>();
    private ThreadLocal<LoggingEvent> loggingEvent = new ThreadLocal<LoggingEvent>();
    private static final LoggingContext instance = new LoggingContext();
    private Timer stackTraceTimer = Monitors.newTimer("getStacktraceElement", TimeUnit.NANOSECONDS);

    private LoggingContext() {
    }

   

   
    /**
     * Gets the starting calling stack trace element of a given stack which
     * matches the given class name. Given the wrapper class name, the match
     * continues until the last stack trace element of the wrapper class is
     * matched
     * 
     * @param stackClass
     *            - The class to be matched for. Get the last matching class
     *            down the stack
     * @param wrapperClassName
     *            - The wrapper class to be matched for
     * @param stArray
     *            - The stack which can identify the caller
     * @return - StackTraceElement which denotes the calling point of given
     *         class or wrapper class
     */
    public StackTraceElement getStackTraceElement(Class stackClass) {
        
        Stopwatch s = stackTraceTimer.start();
        // Get the throwable stack. This is not as expensive as we think
        // 3000 nano seconds average for 1 million executions on my machine
        Throwable t = new Throwable();
        StackTraceElement[] stArray = t.getStackTrace();
        int stackSize = stArray.length;
        StackTraceElement st = null;
        boolean matched = false;
        for (int i = 0; i < stackSize; i++) {
           boolean found = false;
           while (stArray[i].getClassName().equals(stackClass.getName())
                   ) {
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

    public LocationInfo getLocationInfo(Class wrapperClassName) {
        if (stackLocal.get() == null) {
        stackLocal.set(this.getStackTraceElement(wrapperClassName));
         }
        LocationInfo locationInfo = new LocationInfo(stackLocal.get().getFileName(), stackLocal.get().getClassName(), 
                stackLocal.get().getMethodName(), stackLocal.get().getLineNumber() + "");
        return locationInfo;
    }
    
    /**
     * Clears the location information set by {{@link #setLocationInfo(ContextType)}. This needs to be cleared
     * at the end of every logging call, otherwise the location information would be stale and would not indicate
     * the right caller.
     */
   void clearLocationInfo() {
        MDC.remove("locationInfo");
        stackLocal.set(null);
    }
    
    public static LoggingContext getInstance() {
        return instance;
    }
    
    public LocationInfo generateLocationInfo(LoggingEvent event) {
        if (event != loggingEvent.get()) {
            loggingEvent.set(event);
            clearLocationInfo();
        }
        LocationInfo locationInfo = null;
        try {
            locationInfo = (LocationInfo) LoggingContext.getInstance()
                    .getLocationInfo(
                            Class.forName(event.getFQNOfLoggerClass()));
            MDC.put("locationInfo", locationInfo);
        } catch (Throwable e) {
           e.printStackTrace();
           
        }
        return locationInfo;
    }
    
    public LocationInfo getLocationInfo(LoggingEvent event) {
        if (event != loggingEvent.get()) {
            loggingEvent.set(event);
            clearLocationInfo();
        }

    // For async appenders, the locationInfo is set in the MDC and not with the thread. Check for that first
    LocationInfo locationInfo = (LocationInfo)event.getMDC("locationInfo");
       if (locationInfo == null) {
        locationInfo = this.generateLocationInfo(event);
        }
    
        return locationInfo;
    }
 
}
