package com.netflix.eureka2.utils;

import java.lang.reflect.Constructor;

/**
 * @author Tomasz Bak
 */
public final class ExceptionUtils {

    private ExceptionUtils() {
    }

    /**
     * Create a new exception with a stack trace containing only the callers frame item.
     * It is useful for exceptions created in static initializers, where full stack is not needed,
     * is misleading, and in case of a base class and multiple inheritance gives false direction of the source
     * of the error. The stack trace in the latter case will depend on which of the derived classes is instantiated
     * first.
     */
    public static <E extends Exception> E detachedExceptionOf(Class<E> type, String message) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace.length > 2) {
            stackTrace = new StackTraceElement[]{stackTrace[2]};
        }
        E exception;
        try {
            Constructor<E> constructor = type.getConstructor(String.class);
            exception = constructor.newInstance(message);
            exception.setStackTrace(stackTrace);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot instantiate an exception of class " + type, e);
        }
        return exception;
    }
}
