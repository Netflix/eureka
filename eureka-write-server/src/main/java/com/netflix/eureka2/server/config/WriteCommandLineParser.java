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

import com.netflix.eureka2.server.WriteServerConfig;
import com.netflix.eureka2.server.WriteServerConfig.WriteServerConfigBuilder;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import static com.netflix.eureka2.transport.EurekaTransports.DEFAULT_REGISTRATION_PORT;
import static com.netflix.eureka2.transport.EurekaTransports.DEFAULT_REPLICATION_PORT;

/**
 * @author Tomasz Bak
 */
public class WriteCommandLineParser extends EurekaCommandLineParser<WriteServerConfig, WriteServerConfigBuilder> {
    public WriteCommandLineParser(String... args) {
        super(new WriteServerConfigBuilder(), false, args);
    }

    @Override
    protected void additionalOptions(Options options) {
        options.addOption("w", true, "TCP registration server port; default " + DEFAULT_REGISTRATION_PORT);
        options.addOption("p", true, "TCP replication server port; default " + DEFAULT_REPLICATION_PORT);
    }

    @Override
    protected void process(CommandLine cli) {
        super.process(cli);
        builder.withWriteServerPort(Integer.parseInt(cli.getOptionValue("w", "" + DEFAULT_REGISTRATION_PORT)));
        builder.withReplicationPort(Integer.parseInt(cli.getOptionValue("p", "" + DEFAULT_REPLICATION_PORT)));
    }
}
