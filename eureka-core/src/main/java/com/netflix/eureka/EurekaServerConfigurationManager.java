/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.eureka;


/**
 * 
 * The class that caches the configuration of the instance of
 * {@link EurekaServerConfig} that the server started with.
 * 
 * @author Karthik Ranganathan.
 * 
 */
public class EurekaServerConfigurationManager {
    private EurekaServerConfig config;
    private static final EurekaServerConfigurationManager instance = new EurekaServerConfigurationManager();

    private EurekaServerConfigurationManager() {
    }

    public static EurekaServerConfigurationManager getInstance() {
        return instance;
    }

    public void setConfiguration(EurekaServerConfig config) {
        this.config = config;
    }

    public EurekaServerConfig getConfiguration() {
        return this.config;
    }
}
