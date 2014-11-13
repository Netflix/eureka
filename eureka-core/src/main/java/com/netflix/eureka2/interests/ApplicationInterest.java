package com.netflix.eureka2.interests;

import com.netflix.eureka2.registry.InstanceInfo;

import java.util.regex.Pattern;

/**
 * @author Nitesh Kant
 */
public class ApplicationInterest extends Interest<InstanceInfo> {

    private final String applicationName;
    private final Operator operator;

    private volatile Pattern pattern;

    protected ApplicationInterest() {
        applicationName = null;
        operator = null;
    }

    public ApplicationInterest(String applicationName) {
        this.applicationName = applicationName;
        this.operator = Operator.Equals;
    }

    public ApplicationInterest(String applicationName, Operator operator) {
        this.applicationName = applicationName;
        this.operator = operator;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public Operator getOperator() {
        return operator;
    }

    @Override
    public boolean matches(InstanceInfo data) {
        if (operator != Operator.Like) {
            return applicationName.equals(data.getApp());
        }
        if (pattern == null) {
            pattern = Pattern.compile(applicationName);
        }
        return pattern.matcher(data.getApp()).matches();
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
        if (operator != that.operator) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = applicationName != null ? applicationName.hashCode() : 0;
        result = 31 * result + (operator != null ? operator.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ApplicationInterest{applicationName='" + applicationName + '\'' + ", operator=" + operator + '}';
    }
}
