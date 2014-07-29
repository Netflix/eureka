package com.netflix.eureka.protocol.registration;

/**
 * @author Tomasz Bak
 */
public class Register {
    // InstanceInfo class once defined.
    private final Object instanceInfo;

    public Register(Object instanceInfo) {
        this.instanceInfo = instanceInfo;
    }

    public Object getInstanceInfo() {
        return instanceInfo;
    }
}
