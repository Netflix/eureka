/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.testkit.data.builder;

import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.model.instance.InstanceInfo;

/**
 * @author Tomasz Bak
 */
public enum SampleInterest {

    ZuulInstance() {
        @Override
        public Interest<InstanceInfo> build() {
            return Interests.forInstances(SampleInstanceInfo.ZuulServer.build().getId());
        }
    },
    ZuulVip() {
        @Override
        public Interest<InstanceInfo> build() {
            return Interests.forVips(SampleInstanceInfo.ZuulServer.build().getVipAddress());
        }
    },
    ZuulVipSecure() {
        @Override
        public Interest<InstanceInfo> build() {
            return Interests.forSecureVips(SampleInstanceInfo.ZuulServer.build().getVipAddress());
        }
    },
    ZuulApp() {
        @Override
        public Interest<InstanceInfo> build() {
            return Interests.forApplications(SampleInstanceInfo.ZuulServer.build().getApp());
        }
    },
    DiscoveryInstance() {
        @Override
        public Interest<InstanceInfo> build() {
            return Interests.forInstances(SampleInstanceInfo.DiscoveryServer.build().getId());
        }
    },
    DiscoveryVip() {
        @Override
        public Interest<InstanceInfo> build() {
            return Interests.forVips(SampleInstanceInfo.DiscoveryServer.build().getVipAddress());
        }
    },
    DiscoveryVipSecure() {
        @Override
        public Interest<InstanceInfo> build() {
            return Interests.forSecureVips(SampleInstanceInfo.DiscoveryServer.build().getVipAddress());
        }
    },
    DiscoveryApp() {
        @Override
        public Interest<InstanceInfo> build() {
            return Interests.forApplications(SampleInstanceInfo.DiscoveryServer.build().getApp());
        }
    },
    MultipleApps() {
        @Override
        public Interest<InstanceInfo> build() {
            return Interests.forSome(ZuulInstance.build(), DiscoveryApp.build(), ZuulVipSecure.build(), DiscoveryInstance.build());
        }
    };

    public abstract Interest<InstanceInfo> build();
}
