package com.netflix.eureka.transport;

/**
 * @author Tomasz Bak
 */
public class UserContent extends Message {

    // We do not use type parameter here, as we cannot handle generics on the wire
    private final Object content;

    // For dynamic object creation
    protected UserContent() {
        content = null;
    }

    public UserContent(Object content) {
        this.content = content;
    }

    public Object getContent() {
        return content;
    }
}
