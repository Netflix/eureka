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

package com.netflix.rx.eureka.interests;

import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.registry.SampleInstanceInfo;

/**
 * @author Tomasz Bak
 */
public enum SampleInterest {

    ZuulVip() {
        @Override
        public Interest<InstanceInfo> build() {
            return Interests.forVip(SampleInstanceInfo.ZuulServer.build().getVipAddress());
        }
    },
    ZuulApp() {
        @Override
        public Interest<InstanceInfo> build() {
            return Interests.forApplication(SampleInstanceInfo.ZuulServer.build().getApp());
        }
    },
    DiscoveryVip() {
        @Override
        public Interest<InstanceInfo> build() {
            return Interests.forApplication(SampleInstanceInfo.DiscoveryServer.build().getVipAddress());
        }
    },
    DiscoveryApp() {
        @Override
        public Interest<InstanceInfo> build() {
            return Interests.forApplication(SampleInstanceInfo.DiscoveryServer.build().getApp());
        }
    },
    MultipleApps() {
        @Override
        public Interest<InstanceInfo> build() {
            return Interests.forSome(ZuulApp.build(), DiscoveryApp.build());
        }
    };

    public abstract Interest<InstanceInfo> build();
}
