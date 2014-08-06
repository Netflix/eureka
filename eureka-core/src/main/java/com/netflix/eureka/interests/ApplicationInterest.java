package com.netflix.eureka.interests;

import com.netflix.eureka.registry.InstanceInfo;

/**
 * @author Nitesh Kant
 */
public class ApplicationInterest extends Interest<InstanceInfo> {

    private final String applicationName;

    protected ApplicationInterest() {
        applicationName = null;
    }

    public ApplicationInterest(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getApplicationName() {
        return applicationName;
    }

    @Override
    public boolean matches(InstanceInfo data) {
        return applicationName.equals(data.getApp());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ApplicationInterest that = (ApplicationInterest) o;

        if (applicationName != null ? !applicationName.equals(that.applicationName) : that.applicationName != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return applicationName != null ? applicationName.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ApplicationInterest{applicationName='" + applicationName + '\'' + '}';
    }
}
