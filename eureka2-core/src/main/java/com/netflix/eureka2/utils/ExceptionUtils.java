package com.netflix.eureka2.utils;

/**
 * @author Tomasz Bak
 */
public final class ExceptionUtils {

    private ExceptionUtils() {
    }

    /**
     * Trim stack trace in the provided exception to contain only the caller's frame item.
     * This is useful for exceptions created in static initializers, where full stack is not needed,
     * is misleading, and in case of a base class and multiple inheritance gives false direction of the source
     * of the error. The stack trace in the latter case will depend on which of the derived classes is instantiated
     * first.
     */
    public static <E extends Exception> E trimStackTraceof(E exception) {
        StackTraceElement[] stackTrace = exception.getStackTrace();
        if (stackTrace.length > 0) {
            exception.setStackTrace(new StackTraceElement[]{stackTrace[0]});
        }
        return exception;
    }
}
