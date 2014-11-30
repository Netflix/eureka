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

package com.netflix.eureka2.server.config;

import com.netflix.eureka2.server.config.EurekaServerConfig.DefaultEurekaServerConfigBuilder;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import static com.netflix.eureka2.transport.EurekaTransports.DEFAULT_DISCOVERY_PORT;

/**
 * @author Tomasz Bak
 */
public class ReadCommandLineParser extends EurekaCommandLineParser<EurekaServerConfig, DefaultEurekaServerConfigBuilder> {

    public ReadCommandLineParser(String... args) {
        super(EurekaServerConfig.baseBuilder(), true, args);
    }

    @Override
    protected void additionalOptions(Options options) {
        options.addOption("q", true, "server resolver type (dns|inline); default inline");
        options.addOption("r", true, "TCP discovery server port; default " + DEFAULT_DISCOVERY_PORT);
    }

    @Override
    protected void process(CommandLine cli) {
        super.process(cli);
        builder.withDiscoveryPort(Integer.parseInt(cli.getOptionValue("r", "" + DEFAULT_DISCOVERY_PORT)));
    }
}
