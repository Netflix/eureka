/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.eureka2.performance.transport;

import com.netflix.eureka2.spi.model.transport.ProtocolMessageEnvelope.ProtocolType;
import org.apache.commons.cli.*;

/**
 */
class Configuration {
    private ProtocolType protocolType;

    public ProtocolType getProtocolType() {
        return protocolType;
    }

    static Configuration parseCommandLineArgs(String[] args) {
        Options options = new Options()
                .addOption("h", false, "print this help information")
                .addOption(
                        OptionBuilder.withLongOpt("channel").hasArg().withType(String.class)
                                .withDescription("registration | interest | replication").create()
                );

        Configuration config = new Configuration();
        try {
            CommandLine cli = new PosixParser().parse(options, args);

            if (cli.hasOption("-h")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("TransportPerf", "Options:", options, null, true);
                System.out.println();
                System.out.println("For example:");
                System.out.println();
                System.out.println("    TransportPerf --channel interest");
                return null;
            }

            if (cli.hasOption("--channel")) {
                String channel = cli.getOptionValue("channel");
                if ("registration".equalsIgnoreCase(channel)) {
                    config.protocolType = ProtocolType.Registration;
                } else if ("interest".equalsIgnoreCase(channel)) {
                    config.protocolType = ProtocolType.Interest;
                } else if ("replication".equalsIgnoreCase(channel)) {
                    config.protocolType = ProtocolType.Replication;
                } else {
                    throw new IllegalArgumentException("Bad channel type (expected [registration | interest | replication])");
                }
            } else {
                config.protocolType = ProtocolType.Registration;
            }
        } catch (ParseException e) {
            throw new IllegalArgumentException("invalid command line parameters; " + e);
        }

        return config;
    }
}
