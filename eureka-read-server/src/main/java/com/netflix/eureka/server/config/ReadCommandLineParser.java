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

package com.netflix.eureka.server.config;

import com.netflix.eureka.server.ReadServerConfig;
import com.netflix.eureka.server.ReadServerConfig.ReadServerConfigBuilder;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import static com.netflix.eureka.transport.EurekaTransports.*;

/**
 * @author Tomasz Bak
 */
public class ReadCommandLineParser extends EurekaCommandLineParser<ReadServerConfig, ReadServerConfigBuilder> {

    public ReadCommandLineParser(String... args) {
        super(new ReadServerConfigBuilder(), true, args);
    }

    @Override
    protected void additionalOptions(Options options) {
        options.addOption("q", true, "server resolver type (dns|inline); default inline");
        options.addOption("rw", true, "write cluster TCP registration server port; default " + DEFAULT_REGISTRATION_PORT);
        options.addOption("rr", true, "write cluster TCP discovery server port; default " + DEFAULT_DISCOVERY_PORT);
    }

    @Override
    protected void process(CommandLine cli) {
        super.process(cli);
        builder.withWriteClusterRegistrationPort(Integer.parseInt(cli.getOptionValue("rw", "" + DEFAULT_REGISTRATION_PORT)));
        builder.withWriteClusterDiscoveryPort(Integer.parseInt(cli.getOptionValue("rr", "" + DEFAULT_DISCOVERY_PORT)));
    }
}
