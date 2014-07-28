package com.netflix.eureka.transport;

/**
 * @author Tomasz Bak
 */
public class UserContentWithAck extends UserContent {
    private final String correlationId;
    private final long timeout;

    // For dynamic object creation
    protected UserContentWithAck() {
        correlationId = null;
        timeout = 0;
    }

    public UserContentWithAck(Object body, String correlationId, long timeout) {
        super(body);
        this.correlationId = correlationId;
        this.timeout = timeout;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public long getTimeout() {
        return timeout;
    }
}
