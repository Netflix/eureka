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

package com.netflix.eureka.client.bootstrap;

import java.net.SocketAddress;
import java.util.List;

import com.netflix.eureka.client.BootstrapResolver;

/**
 * Provide means to use multiple sources of bootstrap server list.
 *
 * @author Tomasz Bak
 */
public class BootstrapResolverFailoverChain implements BootstrapResolver {
    @Override
    public List<SocketAddress> resolveWriteClusterServers() {
        return null;
    }
}
