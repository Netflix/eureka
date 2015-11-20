package com.netflix.eureka.guice;

import com.netflix.karyon.conditional.ConditionalOnEc2;

/**
 * @author David Liu
 */
@ConditionalOnEc2
public class Ec2EurekaClientModule extends com.netflix.discovery.guice.Ec2EurekaClientModule {
    @Override
    public boolean equals(Object obj) {
        return Ec2EurekaClientModule.class.equals(obj.getClass());
    }

    @Override
    public int hashCode() {
        return Ec2EurekaClientModule.class.hashCode();
    }
}
