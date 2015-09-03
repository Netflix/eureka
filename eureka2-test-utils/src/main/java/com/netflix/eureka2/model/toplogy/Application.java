package com.netflix.eureka2.model.toplogy;

/**
 * Models an application, that consists of a number of servers.
 *
 * @author Tomasz Bak
 */
public class Application {
    private final String name;
    private final ApplicationProfile profile;
    private int serviceIdx;

    public Application(String name, ApplicationProfile profile) {
        this.name = name;
        this.profile = profile;
    }

    public String getName() {
        return name;
    }

    public ApplicationProfile getProfile() {
        return profile;
    }

    public String getServiceName() {
        return name + "#service_" + serviceIdx++;
    }
}
