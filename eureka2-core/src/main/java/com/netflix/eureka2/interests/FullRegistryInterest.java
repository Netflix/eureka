package com.netflix.eureka2.interests;

/**
 * @author Nitesh Kant
 */
public class FullRegistryInterest<T> implements Interest<T> {

    private static final FullRegistryInterest<?> DEFAULT_INSTANCE = new FullRegistryInterest<>();

    private static final int HASH = 234234128;

    public static <T> FullRegistryInterest<T> getInstance() {
        return (FullRegistryInterest<T>) DEFAULT_INSTANCE;
    }

    @Override
    public boolean matches(T data) {
        return true;
    }

    @Override
    public boolean isAtomicInterest() {
        return true;
    }

    @Override
    public int hashCode() {
        return HASH;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FullRegistryInterest;
    }
}
