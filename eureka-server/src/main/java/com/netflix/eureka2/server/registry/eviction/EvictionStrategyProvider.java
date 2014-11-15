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

package com.netflix.eureka2.server.registry.eviction;

import javax.inject.Inject;
import javax.inject.Provider;

import com.netflix.eureka2.server.EurekaBootstrapConfig;

/**
 * @author Tomasz Bak
 */
public class EvictionStrategyProvider implements Provider<EvictionStrategy> {

    public enum StrategyType {
        PercentageDrop
    }

    private final EurekaBootstrapConfig config;

    @Inject
    public EvictionStrategyProvider(EurekaBootstrapConfig config) {
        this.config = config;
    }

    @Override
    public EvictionStrategy get() {
        StrategyType type = StrategyType.valueOf(config.getEvictionStrategyType());
        switch (type) {
            case PercentageDrop:
                return new PercentageDropEvictionStrategy(Integer.parseInt(config.getEvictionStrategyValue()));
        }
        throw new IllegalArgumentException("Unrecognized eviction strategy " + type);
    }
}
