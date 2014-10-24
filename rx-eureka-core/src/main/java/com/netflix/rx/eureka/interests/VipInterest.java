package com.netflix.rx.eureka.interests;

import java.util.regex.Pattern;

import com.netflix.rx.eureka.registry.InstanceInfo;

/**
 * @author Nitesh Kant
 */
public class VipInterest extends Interest<InstanceInfo> {

    private final String vip;
    private final Operator operator;

    private volatile Pattern pattern;

    protected VipInterest() {
        vip = null;
        operator = null;
    }

    public VipInterest(String vip) {
        this.vip = vip;
        this.operator = Operator.Equals;
    }

    public VipInterest(String vip, Operator operator) {
        this.vip = vip;
        this.operator = operator;
    }

    public String getVip() {
        return vip;
    }

    public Operator getOperator() {
        return operator;
    }

    @Override
    public boolean matches(InstanceInfo data) {
        if (operator != Operator.Like) {
            return vip.equals(data.getVipAddress());
        }
        if (pattern == null) {
            pattern = Pattern.compile(vip);
        }
        return pattern.matcher(data.getVipAddress()).matches();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        VipInterest that = (VipInterest) o;

        if (operator != that.operator) {
            return false;
        }
        if (vip != null ? !vip.equals(that.vip) : that.vip != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = vip != null ? vip.hashCode() : 0;
        result = 31 * result + (operator != null ? operator.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "VipInterest{operator=" + operator + ", vip='" + vip + '\'' + '}';
    }
}
