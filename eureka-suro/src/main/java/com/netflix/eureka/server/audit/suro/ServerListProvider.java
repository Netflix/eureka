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

package com.netflix.eureka.server.audit.suro;

import javax.inject.Inject;
import javax.inject.Provider;

import com.netflix.eureka.client.bootstrap.StaticServerResolver;
import com.netflix.eureka.server.spi.ExtensionContext;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;

/**
 * @author Tomasz Bak
 */
public class ServerListProvider implements Provider<ServerList<Server>> {

    /**
     * Suro server list can be injected directly via configuration, in the following format:
     * host[:port][;host[:port...]]
     */
    private static final String SURO_SERVER_LIST_KEY = "eureka.ext.suro.servers";

    private final ExtensionContext context;

    @Inject
    public ServerListProvider(ExtensionContext context) {
        this.context = context;
    }

    @Override
    public ServerList<Server> get() {
        String propertyValue = context.properties().getProperty(SURO_SERVER_LIST_KEY);
        ServerList<Server> servers;
        if (propertyValue == null || (propertyValue = propertyValue.trim()).isEmpty()) {
            servers = new EurekaSourcedServerList(new StaticServerResolver<>(context.interalReadServerAddress()));
        } else {
            servers = new PropertySourcedServerList(propertyValue);
        }
        return servers;
    }
}
