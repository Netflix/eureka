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

package com.netflix.eureka2;

import com.netflix.eureka2.server.config.EurekaCommandLineParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import static com.netflix.eureka2.EurekaDashboardConfig.*;
import static com.netflix.eureka2.transport.EurekaTransports.DEFAULT_DISCOVERY_PORT;

/**
 * @author Tomasz Bak
 */
public class DashboardCommandLineParser extends EurekaCommandLineParser<EurekaDashboardConfig, EurekaDashboardConfigBuilder> {
    protected DashboardCommandLineParser(String... args) {
        super(new EurekaDashboardConfigBuilder(), true, args);
    }

    @Override
    protected void additionalOptions(Options options) {
        options.addOption("b", true, "HTTP dashboard server port; default " + DEFAULT_DASHBOARD_PORT);
        options.addOption("rr", true, "write cluster TCP discovery server port; default " + DEFAULT_DISCOVERY_PORT);
    }

    @Override
    protected void process(CommandLine cli) {
        super.process(cli);
        builder.withDashboardPort(Integer.parseInt(cli.getOptionValue("b", "" + DEFAULT_DASHBOARD_PORT)));
        builder.withWriteClusterDiscoveryPort(Integer.parseInt(cli.getOptionValue("rr", "" + DEFAULT_DISCOVERY_PORT)));
    }
}
