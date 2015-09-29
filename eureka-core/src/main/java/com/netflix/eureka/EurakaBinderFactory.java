package com.netflix.eureka;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;

public class EurakaBinderFactory {

    public static EurakaBinder newBinder() {
        InstanceInfo info = ApplicationInfoManager.getInstance().getInfo();

        // Only in AWS, enable the binding functionality
        if (DataCenterInfo.Name.Amazon.equals(info.getDataCenterInfo().getName())) {
            BindingStrategy bindingStrategy = EurekaServerConfigurationManager.getInstance().getConfiguration().getBindingStrategy();
            switch (bindingStrategy) {
                case ROUTE53:
                    return new Route53EurekaBinder();
                case EIP:
                    return new EIPEurekaBinder();
                default:
                    throw new IllegalArgumentException("Unexpected BindingStrategy " + bindingStrategy);
            }
        }

        return new NullEurekaBinder();
    }
}
