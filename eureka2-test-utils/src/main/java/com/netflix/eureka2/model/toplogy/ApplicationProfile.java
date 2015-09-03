package com.netflix.eureka2.model.toplogy;

/**
 * Application topology properties, like number of different applications of a given type
 * or number of services/servers per application.
 *
 * @author Tomasz Bak
 */
public class ApplicationProfile {
    private final String applicationName;
    private final int applicationSize;
    private final int quantity;

    public ApplicationProfile(String applicationName, int applicationSize, int quantity) {
        this.applicationName = applicationName;
        this.applicationSize = applicationSize;
        this.quantity = quantity;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public int getApplicationSize() {
        return applicationSize;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getServiceCount() {
        return applicationSize * quantity;
    }
}
