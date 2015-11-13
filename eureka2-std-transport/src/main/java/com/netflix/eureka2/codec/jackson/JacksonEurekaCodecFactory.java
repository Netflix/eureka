/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.eureka2.codec.jackson;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.netflix.eureka2.spi.codec.EurekaCodec;
import com.netflix.eureka2.spi.codec.EurekaCodecFactory;

/**
 */
public class JacksonEurekaCodecFactory extends EurekaCodecFactory {

    private final Set<Class<?>> acceptTypes;
    private final JacksonEurekaCodec codec;

    public JacksonEurekaCodecFactory(Class<?>... acceptTypes) {
        this(new HashSet<>(Arrays.asList(acceptTypes)));
    }

    public JacksonEurekaCodecFactory(Set<Class<?>> acceptTypes) {
        this.acceptTypes = acceptTypes;
        this.codec = new JacksonEurekaCodec(acceptTypes);
    }

    @Override
    public <T> boolean accept(Class<T> type) {
        return acceptTypes.contains(type);
    }

    @Override
    public EurekaCodec getCodec() {
        return codec;
    }
}
