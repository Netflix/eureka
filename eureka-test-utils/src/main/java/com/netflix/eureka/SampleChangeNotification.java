package com.netflix.eureka;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.registry.InstanceInfo;

/**
 * @author David Liu
 */
public enum SampleChangeNotification {

    ZuulAddNotification() {
        @Override
        public ChangeNotification<InstanceInfo> newNotification() {
            return newNotification(SampleInstanceInfo.ZuulServer.build());
        }

        @Override
        public ChangeNotification<InstanceInfo> newNotification(InstanceInfo seed) {
            return new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Add, seed);
        }
    },
    ZuulDeleteNotification() {
        @Override
        public ChangeNotification<InstanceInfo> newNotification() {
            return newNotification(SampleInstanceInfo.ZuulServer.build());
        }

        @Override
        public ChangeNotification<InstanceInfo> newNotification(InstanceInfo seed) {
            return new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Delete, seed);
        }
    },
    DiscoveryAddNotification() {
        @Override
        public ChangeNotification<InstanceInfo> newNotification() {
            return newNotification(SampleInstanceInfo.DiscoveryServer.build());
        }

        @Override
        public ChangeNotification<InstanceInfo> newNotification(InstanceInfo seed) {
            return new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Add, seed);
        }
    },
    DiscoveryDeleteNotification() {
        @Override
        public ChangeNotification<InstanceInfo> newNotification() {
            return newNotification(SampleInstanceInfo.DiscoveryServer.build());
        }

        @Override
        public ChangeNotification<InstanceInfo> newNotification(InstanceInfo seed) {
            return new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Delete, seed);
        }
    };

    public abstract ChangeNotification<InstanceInfo> newNotification();
    public abstract ChangeNotification<InstanceInfo> newNotification(InstanceInfo seed);

}
