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

import java.util.List;

import com.netflix.eureka.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import static com.netflix.eureka.transport.EurekaTransports.*;

/**
 * This class helps to gather all the information that are deployment environment
 * specific.
 *
 * @author Tomasz Bak
 */
public class StartupConfig {

    private final boolean helpOption;
    private final String resolverType;
    private final int readServerPort;
    private final int shutDownPort;
    private final DataCenterType dataCenterType;
    private final String appName;
    private final String vipAddress;
    private final String[] rest;

    public StartupConfig(boolean helpOption, DataCenterType dataCenterType, String resolverType, int readServerPort, int shutDownPort,
                         String appName, String vipAddress, String[] rest) {
        this.helpOption = helpOption;
        this.resolverType = resolverType;
        this.readServerPort = readServerPort;
        this.shutDownPort = shutDownPort;
        this.dataCenterType = dataCenterType;
        this.appName = appName;
        this.vipAddress = vipAddress;
        this.rest = rest;
    }

    public DataCenterType getDataCenterType() {
        return dataCenterType;
    }

    public String getResolverType() {
        return resolverType;
    }

    public int getDiscoveryPort() {
        return readServerPort;
    }

    public int getShutDownPort() {
        return shutDownPort;
    }

    public String getAppName() {
        return appName;
    }

    public String getVipAddress() {
        return vipAddress;
    }

    public String[] getRest() {
        return rest;
    }

    public boolean hasHelp() {
        return helpOption;
    }

    public static class StartupConfigBuilder<C extends StartupConfig, B extends StartupConfigBuilder<C, B>> {
        protected boolean helpOption;
        protected DataCenterType dataCenterType;
        protected String resolverType;
        protected int readServerPort;
        protected int shutDownPort;
        protected String appName;
        protected String vipAddress;
        protected String[] rest;

        protected StartupConfigBuilder() {
        }

        public B withHelpOption(boolean helpOption) {
            this.helpOption = helpOption;
            return self();
        }

        public B withResolverType(String resolverType) {
            this.resolverType = resolverType;
            return self();
        }

        public B withReadServerPort(int readServerPort) {
            this.readServerPort = readServerPort;
            return self();
        }

        public B withShutDownPort(int shutDownPort) {
            this.shutDownPort = shutDownPort;
            return self();
        }

        public B withDataCenterType(DataCenterType dataCenterType) {
            this.dataCenterType = dataCenterType;
            return self();
        }

        public B withAppName(String appName) {
            this.appName = appName;
            return self();
        }

        public B withVipAddress(String vipAddress) {
            this.vipAddress = vipAddress;
            return self();
        }

        public B withRest(String[] rest) {
            this.rest = rest;
            return self();
        }

        @SuppressWarnings("unchecked")
        public C build() {
            return (C) new StartupConfig(helpOption, dataCenterType, resolverType, readServerPort, shutDownPort, appName, vipAddress, rest);
        }

        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }
    }

    public static class EurekaCommandLineParser<C extends StartupConfig> {

        private final Options options;
        private final boolean resolverRequired;

        protected boolean helpOption;
        protected String resolverType;
        protected int readServerPort;
        protected DataCenterType dataCenterType;
        protected int shutDownPort;
        protected String appName;
        protected String vipAddress;
        protected String[] rest;

        protected EurekaCommandLineParser(boolean resolverRequired) {
            this.resolverRequired = resolverRequired;
            this.options = new Options()
                    .addOption("h", false, "print this help information")
                    .addOption("d", true, "datacenter type (AWS|Basic). Default Basic")
                    .addOption("q", true, "server resolver type (dns|inline); default inline")
                    .addOption("s", true, "shutdown port; default 7700")
                    .addOption("r", true, "TCP discovery server port; default " + DEFAULT_DISCOVERY_PORT)
                    .addOption("n", true, "server instance name");
        }

        protected void additionalOptions(Options options) {
        }

        @SuppressWarnings("unchecked")
        protected C build() {
            return (C) new StartupConfig(helpOption, dataCenterType, resolverType, readServerPort, shutDownPort, appName, vipAddress, rest);
        }

        @SuppressWarnings("unchecked")
        protected void process(CommandLine cli) {
            helpOption = cli.hasOption('h');
            if (!helpOption) {
                dataCenterType = DataCenterType.valueOf(cli.getOptionValue("d", "Basic"));

                if (resolverRequired || !cli.getArgList().isEmpty()) {
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
                }

                shutDownPort = Integer.parseInt(cli.getOptionValue("s", "7700"));
                readServerPort = Integer.parseInt(cli.getOptionValue("r", "" + DEFAULT_DISCOVERY_PORT));
                if (!cli.hasOption("n")) {
                    throw new IllegalArgumentException("missing required server name option ('-n <server_name>')");
                }
                appName = vipAddress = cli.getOptionValue("n");
                rest = ((List<String>) cli.getArgList()).toArray(new String[cli.getArgList().size()]);
            }
        }

        public C build(String... args) {
            additionalOptions(options);
            try {
                CommandLine cli = new PosixParser().parse(options, args);
                process(cli);
                return build();
            } catch (ParseException e) {
                throw new IllegalArgumentException("invalid command line parameters; " + e);
            }
        }

        public void printHelp() {
            new HelpFormatter().printHelp("syntax", options);
        }
    }
}
