package com.netflix.eureka;

public class NullEurekaBinder implements EurakaBinder {
    @Override
    public void bind() throws InterruptedException {
    }

    @Override
    public void unbind() throws InterruptedException {
    }
}
