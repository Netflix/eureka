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

import com.netflix.eureka.interests.ChangeNotification;
import rx.Observable;
import rx.functions.Func1;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author David Liu
 */
public class DeltaInstanceInfo {
    public HashSet<Delta> deltas;

    // for serializers
    private DeltaInstanceInfo() {
        deltas = new HashSet<Delta>();
    }

    public Set<Delta> getDeltas() {
        return Collections.unmodifiableSet(deltas);
    }

    public InstanceInfo applyTo(InstanceInfo instanceInfo) {
        return new InstanceInfo.Builder().withInstanceInfo(instanceInfo).withDeltaInstanceInfo(this).build();
    }

    /**
     * Return a stream of {@link ChangeNotification}s with InstanceInfo data each containing a single delta change
     * @param baseInstanceInfo the base InstanceInfo from which the deltas should be applied
     * @return a stream of {@link ChangeNotification}s with InstanceInfo data each containing a single delta change
     */
    public Observable<ChangeNotification<InstanceInfo>> forDeltas(final InstanceInfo baseInstanceInfo) {
        return Observable.from(deltas)
                .map(new Func1<Delta, ChangeNotification<InstanceInfo>>() {
                    @Override
                    public ChangeNotification<InstanceInfo> call(Delta delta) {
                        InstanceInfo instanceInfo = new InstanceInfo.Builder()
                                .withInstanceInfo(baseInstanceInfo)
                                .withDelta(delta)
                                .build();
                        return new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Modify, instanceInfo);
                    }
                });
    }

    /**
     * Return a stream of {@link ChangeNotification}s with DeltaInstanceInfo data each containing a single delta change
     * @return a stream of {@link ChangeNotification}s with DeltaInstanceInfo data each containing a single delta change
     */
    public Observable<ChangeNotification<DeltaInstanceInfo>> forDeltas() {
        return Observable.from(deltas)
                .map(new Func1<Delta, ChangeNotification<DeltaInstanceInfo>>() {
                    @Override
                    public ChangeNotification<DeltaInstanceInfo> call(Delta delta) {
                        DeltaInstanceInfo singleDelta = new Builder().withDelta(delta).build();
                        return new ChangeNotification<DeltaInstanceInfo>(ChangeNotification.Kind.Modify, singleDelta);

                    }
                });
    }

    public static final class Builder {
        private final DeltaInstanceInfo deltaInstanceInfo;

        public Builder() {
            deltaInstanceInfo = new DeltaInstanceInfo();
        }

        public <T> Builder withDelta(InstanceInfoField<T> field, T value) {
            return withDelta(new Delta<T>(field, value));
        }

        public <T> Builder withDelta(Delta<T> delta) {
            deltaInstanceInfo.deltas.add(delta);
            return this;
        }

        public DeltaInstanceInfo build() {
            return deltaInstanceInfo;
        }
    }
}
