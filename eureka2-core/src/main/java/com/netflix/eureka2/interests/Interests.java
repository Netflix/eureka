package com.netflix.eureka2.interests;

import com.netflix.eureka2.interests.Interest.Operator;
import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * A factory to create instances of {@link Interest}.
 *
 * @author Nitesh Kant
 */
public final class Interests {

    private Interests() {
    }

    public static Interest<InstanceInfo> forVips(String... vips) {
        return forVips(Operator.Equals, vips);
    }

    public static Interest<InstanceInfo> forVips(Operator operator, String... vips) {
        if (vips.length == 0) {
            return EmptyRegistryInterest.getInstance();
        }
        if (vips.length == 1) {
            return new VipInterest(vips[0], operator);
        }
        Interest[] interests = new Interest[vips.length];
        for (int i = 0; i < interests.length; i++) {
            interests[i] = new VipInterest(vips[i], operator);
        }
        return new MultipleInterests<InstanceInfo>(interests);
    }

    public static Interest<InstanceInfo> forSecureVips(String... secureVips) {
        return forSecureVips(Operator.Equals, secureVips);
    }

    public static Interest<InstanceInfo> forSecureVips(Operator operator, String... secureVips) {
        if (secureVips.length == 0) {
            return EmptyRegistryInterest.getInstance();
        }
        if (secureVips.length == 1) {
            return new SecureVipInterest(secureVips[0], operator);
        }
        Interest[] interests = new Interest[secureVips.length];
        for (int i = 0; i < interests.length; i++) {
            interests[i] = new SecureVipInterest(secureVips[i], operator);
        }
        return new MultipleInterests<InstanceInfo>(interests);
    }

    public static Interest<InstanceInfo> forApplications(String... applicationNames) {
        return forApplications(Operator.Equals, applicationNames);
    }

    public static Interest<InstanceInfo> forApplications(Operator operator, String... applicationNames) {
        if (applicationNames.length == 0) {
            return EmptyRegistryInterest.getInstance();
        }
        if (applicationNames.length == 1) {
            return new ApplicationInterest(applicationNames[0], operator);
        }
        Interest[] interests = new Interest[applicationNames.length];
        for (int i = 0; i < interests.length; i++) {
            interests[i] = new ApplicationInterest(applicationNames[i], operator);
        }
        return new MultipleInterests<InstanceInfo>(interests);
    }

    public static Interest<InstanceInfo> forInstances(String... instanceIds) {
        return forInstance(Operator.Equals, instanceIds);
    }

    public static Interest<InstanceInfo> forInstance(Operator operator, String... instanceIds) {
        if (instanceIds.length == 0) {
            return EmptyRegistryInterest.getInstance();
        }
        if (instanceIds.length == 1) {
            return new InstanceInterest(instanceIds[0], operator);
        }
        Interest[] interests = new Interest[instanceIds.length];
        for (int i = 0; i < interests.length; i++) {
            interests[i] = new InstanceInterest(instanceIds[i], operator);
        }
        return new MultipleInterests<InstanceInfo>(interests);
    }

    public static Interest<InstanceInfo> forFullRegistry() {
        return FullRegistryInterest.getInstance();
    }

    public static Interest<InstanceInfo> forNone() {
        return EmptyRegistryInterest.getInstance();
    }

    public static Interest<InstanceInfo> forSome(Interest<InstanceInfo>... interests) {
        return new MultipleInterests<>(interests);
    }
}
