package com.netflix.eureka.transport;

/**
 * @author Tomasz Bak
 */
public class Acknowledgement extends Message {
    private final String correlationId;

    protected Acknowledgement() {
        correlationId = null;
    }

    public Acknowledgement(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
