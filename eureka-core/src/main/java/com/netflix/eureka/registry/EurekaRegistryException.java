package com.netflix.eureka.registry;

/**
 * @author David Liu
 */
public class EurekaRegistryException extends Exception {

    public EurekaRegistryException(Throwable th) {
        super(th);
    }

    public EurekaRegistryException(String msg) {
        super(msg);
    }

    public EurekaRegistryException(String msg, Throwable th) {
        super(msg, th);
    }

    public static EurekaRegistryException instanceNotFound(String instanceId) {
        return new EurekaRegistryException("Instance " + instanceId + "cannot be found");
    }
}
