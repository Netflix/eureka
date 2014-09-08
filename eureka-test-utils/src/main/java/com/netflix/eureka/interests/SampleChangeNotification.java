package com.netflix.eureka.interests;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.SampleInstanceInfo;

/**
 * @author David Liu
 */
public enum SampleChangeNotification {

    ZuulAdd() {
        @Override
        public ChangeNotification<InstanceInfo> newNotification() {
            return newNotification(SampleInstanceInfo.ZuulServer.build());
        }

        @Override
        public ChangeNotification<InstanceInfo> newNotification(InstanceInfo seed) {
            return new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Add, seed);
        }
    },
    ZuulDelete() {
        @Override
        public ChangeNotification<InstanceInfo> newNotification() {
            return newNotification(SampleInstanceInfo.ZuulServer.build());
        }

        @Override
        public ChangeNotification<InstanceInfo> newNotification(InstanceInfo seed) {
            return new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Delete, seed);
        }
    },
    DiscoveryAdd() {
        @Override
        public ChangeNotification<InstanceInfo> newNotification() {
            return newNotification(SampleInstanceInfo.DiscoveryServer.build());
        }

        @Override
        public ChangeNotification<InstanceInfo> newNotification(InstanceInfo seed) {
            return new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Add, seed);
        }
    },
    DiscoveryDelete() {
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
