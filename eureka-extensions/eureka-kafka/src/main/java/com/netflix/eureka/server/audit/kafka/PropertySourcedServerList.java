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

package com.netflix.eureka.server.audit.kafka;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;

/**
 * Server list can be injected directly via configuration, in the following format:
 * host[:port][;host[:port...]]
 *
 * TODO: move it out to different package
 *
 * @author Tomasz Bak
 */
public class PropertySourcedServerList implements ServerList<Server> {

    private static final Pattern SEMICOLON_RE = Pattern.compile(";");

    private final List<Server> servers;

    public PropertySourcedServerList(String propertyValue, int defaultPort) {
        List<Server> servers = new ArrayList<>();
        for (String part : SEMICOLON_RE.split(propertyValue)) {
            int idx = part.indexOf(':');
            String host;
            int port;
            if (idx >= 0) {
                host = part.substring(0, idx);
                port = Integer.parseInt(part.substring(idx + 1));
            } else {
                host = part;
                port = defaultPort;
            }
            servers.add(new Server(host, port));
        }
        this.servers = Collections.unmodifiableList(servers);
    }

    @Override
    public List<Server> getInitialListOfServers() {
        return servers;
    }

    @Override
    public List<Server> getUpdatedListOfServers() {
        return servers;
    }
}
