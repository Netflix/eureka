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
 * Eureka write server bootstrap configuration. Could be provided via command line
 * parameters, built directly using an available builder.
 *
 * TODO: read this information from property file/archaius
 *
 * @author Tomasz Bak
 */
public class WriteStartupConfig extends StartupConfig {
    private final int writeServerPort;
    private final int replicationPort;

    public WriteStartupConfig(boolean helpOption, DataCenterType dataCenterType, String resolverType, int readServerPort, int shutDownPort,
                              String appName, String vipAddress, String[] rest, int writeServerPort, int replicationPort) {
        super(helpOption, dataCenterType, resolverType, readServerPort, shutDownPort, appName, vipAddress, rest);
        this.writeServerPort = writeServerPort;
        this.replicationPort = replicationPort;
    }

    public int getRegistrationPort() {
        return writeServerPort;
    }

    public int getReplicationPort() {
        return replicationPort;
    }

    public static class WriteStartupConfigBuilder extends StartupConfigBuilder<WriteStartupConfig, WriteStartupConfigBuilder> {

        private int writeServerPort;
        private int replicationPort;

        public WriteStartupConfigBuilder withWriteServerPort(int writeServerPort) {
            this.writeServerPort = writeServerPort;
            return this;
        }

        public WriteStartupConfigBuilder withReplicationPort(int replicationPort) {
            this.replicationPort = replicationPort;
            return this;
        }

        @Override
        public WriteStartupConfig build() {
            return new WriteStartupConfig(helpOption, dataCenterType, resolverType, readServerPort, shutDownPort, appName,
                    vipAddress, rest, writeServerPort, replicationPort);
        }
    }

    public static class WriteCommandLineParser extends EurekaCommandLineParser<WriteStartupConfig> {
        private int writeServerPort;
        private int replicationPort;

        protected WriteCommandLineParser() {
            super(false);
        }

        @Override
        protected void additionalOptions(Options options) {
            options.addOption("w", true, "TCP registration server port; default " + DEFAULT_REGISTRATION_PORT);
            options.addOption("p", true, "TCP replication server port; default " + DEFAULT_REPLICATION_PORT);
        }

        @Override
        protected void process(CommandLine cli) {
            super.process(cli);
            writeServerPort = Integer.parseInt(cli.getOptionValue("w", "" + DEFAULT_REGISTRATION_PORT));
            replicationPort = Integer.parseInt(cli.getOptionValue("p", "" + DEFAULT_REPLICATION_PORT));
        }

        @Override
        protected WriteStartupConfig build() {
            return new WriteStartupConfig(helpOption, dataCenterType, resolverType, readServerPort, shutDownPort, appName, vipAddress, rest, writeServerPort, replicationPort);
        }

        @Override
        public String toString() {
            return "WriteCommandLineParser{" +
                    "dataCenterType=" + dataCenterType +
                    ", resolverType=" + resolverType +
                    ", readServerPort=" + readServerPort +
                    ", shutDownPort=" + shutDownPort +
                    ", appName='" + appName + '\'' +
                    ", vipAddress='" + vipAddress + '\'' +
                    ", rest=" + Arrays.toString(rest) + '\'' +
                    ", writeServerPort=" + writeServerPort +
                    ", replicationPort=" + replicationPort +
                    '}';
        }
    }
}
