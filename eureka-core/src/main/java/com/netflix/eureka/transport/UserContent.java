package com.netflix.eureka.transport;

/**
 * @author Tomasz Bak
 */
public class UserContent extends Message {

    // We do not use type parameter here, as we cannot handle generics on the wire
    private final Object body;

    // For dynamic object creation
    protected UserContent() {
        body = null;
    }

    public UserContent(Object body) {
        this.body = body;
    }

    public Object getBody() {
        return body;
    }
}
