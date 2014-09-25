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

package com.netflix.eureka.server;

import java.util.Arrays;

import com.netflix.eureka.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import static com.netflix.eureka.transport.EurekaTransports.*;

/**
 * Eureka read server bootstrap configuration. Could be provided via command line
 * parameters, built directly using an available builder.
 *
 * TODO: read this information from property file/archaius
 *
 * @author Tomasz Bak
 */
public class ReadStartupConfig extends StartupConfig {
    private final String resolverType;
    private final int writeClusterRegistrationPort;
    private final int writeClusterDiscoveryPort;

    public ReadStartupConfig(boolean helpOption, int readServerPort, int shutDownPort, DataCenterType dataCenterType,
                             String appName, String vipAddress, String[] rest, String resolverType,
                             int writeClusterRegistrationPort, int writeClusterDiscoveryPort) {
        super(helpOption, readServerPort, shutDownPort, dataCenterType, appName, vipAddress, rest);
        this.resolverType = resolverType;
        this.writeClusterRegistrationPort = writeClusterRegistrationPort;
        this.writeClusterDiscoveryPort = writeClusterDiscoveryPort;
    }

    public String getResolverType() {
        return resolverType;
    }

    public int getWriteClusterRegistrationPort() {
        return writeClusterRegistrationPort;
    }

    public int getWriteClusterDiscoveryPort() {
        return writeClusterDiscoveryPort;
    }

    public static class ReadStartupConfigBuilder extends StartupConfigBuilder<ReadStartupConfig, ReadStartupConfigBuilder> {
        private String resolverType;

        private int writeClusterRegistrationPort;

        private int writeClusterDiscoveryPort;

        public ReadStartupConfigBuilder withResolverType(String resolverType) {
            this.resolverType = resolverType;
            return this;
        }

        public ReadStartupConfigBuilder withWriteClusterRegistrationPort(int writeClusterRegistrationPort) {
            this.writeClusterRegistrationPort = writeClusterRegistrationPort;
            return this;
        }

        public ReadStartupConfigBuilder withWriteClusterDiscoveryPort(int writeClusterDiscoveryPort) {
            this.writeClusterDiscoveryPort = writeClusterDiscoveryPort;
            return this;
        }

        @Override
        public ReadStartupConfig build() {
            return new ReadStartupConfig(helpOption, readServerPort, shutDownPort, dataCenterType, appName, vipAddress,
                    rest, resolverType, writeClusterRegistrationPort, writeClusterDiscoveryPort);
        }
    }

    public static class ReadCommandLineParser extends EurekaCommandLineParser<ReadStartupConfig> {

        private String resolverType;
        private int writeClusterRegistrationPort;
        private int writeClusterDiscoveryPort;

        @Override
        protected void additionalOptions(Options options) {
            options.addOption("q", true, "server resolver type (dns|inline); default inline");
            options.addOption("rw", true, "write cluster TCP registration server port; default " + DEFAULT_REGISTRATION_PORT);
            options.addOption("rr", true, "write cluster TCP discovery server port; default " + DEFAULT_DISCOVERY_PORT);
        }

        @Override
        protected void process(CommandLine cli) {
            super.process(cli);
            resolverType = cli.getOptionValue("q", "inline");
            switch (resolverType) {
                case "dns":
                    if (cli.getArgList().size() != 1) {
                        throw new IllegalArgumentException("provide Eureka Write cluster domain name as parameter");
                    }
                    break;
                case "inline":
                    if (cli.getArgList().size() < 1) {
                        throw new IllegalArgumentException("provide Eureka Write cluster server addresses as parameter list");
                    }
                    break;
                default:
                    throw new IllegalArgumentException("resolver type not defined ('-r dns|inline')");
            }
            writeClusterRegistrationPort = Integer.parseInt(cli.getOptionValue("rw", "" + DEFAULT_REGISTRATION_PORT));
            writeClusterDiscoveryPort = Integer.parseInt(cli.getOptionValue("rr", "" + DEFAULT_DISCOVERY_PORT));
        }

        @Override
        protected ReadStartupConfig build() {
            return new ReadStartupConfig(helpOption, readServerPort, shutDownPort, dataCenterType, appName,
                    vipAddress, rest, resolverType, writeClusterRegistrationPort, writeClusterDiscoveryPort);
        }

        @Override
        public String toString() {
            return "ReadStartupConfig{" +
                    "readServerPort=" + readServerPort +
                    ", dataCenterType=" + dataCenterType +
                    ", shutDownPort=" + shutDownPort +
                    ", appName='" + appName + '\'' +
                    ", vipAddress='" + vipAddress + '\'' +
                    ", rest=" + Arrays.toString(rest) + '\'' +
                    ", resolverType='" + resolverType + '\'' +
                    ", writeClusterRegistrationPort=" + writeClusterRegistrationPort +
                    ", writeClusterDiscoveryPort=" + writeClusterDiscoveryPort +
                    '}';
        }
    }
}
