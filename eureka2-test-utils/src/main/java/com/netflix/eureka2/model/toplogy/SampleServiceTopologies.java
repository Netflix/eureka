package com.netflix.eureka2.model.toplogy;


import com.netflix.eureka2.model.toplogy.DependencyProfile.DependencyProfileBuilder;
import com.netflix.eureka2.model.toplogy.ServiceTopologyGenerator.ServiceTopologyGeneratorBuilder;

import static java.util.Arrays.asList;

/**
 * @author Tomasz Bak
 */
public enum SampleServiceTopologies {
    Mixed() {
        @Override
        public ServiceTopologyGeneratorBuilder builder() {
            // Big, medium, small app
            ApplicationProfile bigApp = new ApplicationProfile("BigApp", 100, 2);
            ApplicationProfile mediumApp = new ApplicationProfile("MediumApp", 20, 10);
            ApplicationProfile smallApp = new ApplicationProfile("SmallApp", 5, 100);

            // Heavy, medium, light subscriber
            DependencyProfile heavyDep = new DependencyProfileBuilder()
                    .withApplicationProfile(bigApp, 2)
                    .withApplicationProfile(mediumApp, 5)
                    .withApplicationProfile(smallApp, 10)
                    .withQuantity(5)
                    .build();
            DependencyProfile mediumDep = new DependencyProfileBuilder()
                    .withApplicationProfile(bigApp, 1)
                    .withApplicationProfile(mediumApp, 2)
                    .withApplicationProfile(smallApp, 5)
                    .withQuantity(15)
                    .build();
            DependencyProfile lightDep = new DependencyProfileBuilder()
                    .withApplicationProfile(bigApp, 1)
                    .withApplicationProfile(mediumApp, 1)
                    .withApplicationProfile(smallApp, 2)
                    .withQuantity(80)
                    .build();

            return new ServiceTopologyGeneratorBuilder()
                    .withApplicationProfiles(asList(bigApp, mediumApp, smallApp))
                    .withDataCenterInfoGenerator(new DataCenterInfoGenerator())
                    .withDependencyProfiles(asList(heavyDep, mediumDep, lightDep));
        }
    };

    public abstract ServiceTopologyGeneratorBuilder builder();

    public ServiceTopologyGenerator build() {
        return builder().build();
    }
}
