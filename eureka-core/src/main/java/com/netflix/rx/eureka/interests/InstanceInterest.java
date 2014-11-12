package com.netflix.rx.eureka.interests;

import com.netflix.rx.eureka.registry.InstanceInfo;

import java.util.regex.Pattern;

/**
 * @author Nitesh Kant
 */
public class InstanceInterest extends Interest<InstanceInfo> {

    private final String instanceId;
    private final Operator operator;

    private volatile Pattern pattern;

    protected InstanceInterest() {
        instanceId = null;
        operator = null;
    }

    public InstanceInterest(String instanceId) {
        this.instanceId = instanceId;
        this.operator = Operator.Equals;
    }

    public InstanceInterest(String instanceId, Operator operator) {
        this.instanceId = instanceId;
        this.operator = operator;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public Operator getOperator() {
        return operator;
    }

    @Override
    public boolean matches(InstanceInfo data) {
        if (operator != Operator.Like) {
            return instanceId.equals(data.getId());
        }
        if (pattern == null) {
            pattern = Pattern.compile(instanceId);
        }
        return pattern.matcher(data.getId()).matches();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        InstanceInterest that = (InstanceInterest) o;

        if (instanceId != null ? !instanceId.equals(that.instanceId) : that.instanceId != null) {
            return false;
        }
        if (operator != that.operator) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = instanceId != null ? instanceId.hashCode() : 0;
        result = 31 * result + (operator != null ? operator.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "InstanceInterest{instanceId='" + instanceId + '\'' + ", operator=" + operator + '}';
    }
}
