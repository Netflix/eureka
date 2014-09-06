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

package com.netflix.eureka.registry;

import com.netflix.eureka.registry.Delta.Builder;
import com.netflix.eureka.registry.InstanceInfo.Status;

/**
 * @author Tomasz Bak
 */
public enum SampleDelta {

    Delta() {
        @Override
        public Builder builder() {
            return newBuilder();
        }
    },
    StatusUp() {
        @Override
        public Builder builder() {
            return newBuilder().withDelta(InstanceInfoField.STATUS, Status.UP);
        }
    },
    StatusDown() {
        @Override
        public Builder builder() {
            return newBuilder().withDelta(InstanceInfoField.STATUS, Status.DOWN);
        }
    };

    final InstanceInfo baseInstanceInfo;

    SampleDelta() {
        this(SampleInstanceInfo.DiscoveryServer.build());
    }

    SampleDelta(InstanceInfo baseInstanceInfo) {
        this.baseInstanceInfo = baseInstanceInfo;
    }

    public abstract Builder builder();

    public <T> Delta<T> build() {
        return (Delta<T>) builder().build();
    }

    Builder newBuilder() {
        return new Builder()
                .withId(this.baseInstanceInfo.getId())
                .withVersion(this.baseInstanceInfo.getVersion() + 1);
    }
}
