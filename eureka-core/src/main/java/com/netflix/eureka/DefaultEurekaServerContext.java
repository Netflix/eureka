/*
 * Copyright 2015 Netflix, Inc.
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

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.eureka.cluster.PeerEurekaNodes;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.resources.ServerCodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Represent the local server context and exposes getters to components of the
 * local server such as the registry.
 *
 * @author David Liu
 */
@Singleton
public class DefaultEurekaServerContext implements EurekaServerContext {
    private static final Logger logger = LoggerFactory.getLogger(DefaultEurekaServerContext.class);

    private final EurekaServerConfig serverConfig;
    private final ServerCodecs serverCodecs;
    private final PeerAwareInstanceRegistry registry;
    private final PeerEurekaNodes peerEurekaNodes;
    private final ApplicationInfoManager applicationInfoManager;

    @Inject
    public DefaultEurekaServerContext(EurekaServerConfig serverConfig,
                               ServerCodecs serverCodecs,
                               PeerAwareInstanceRegistry registry,
                               PeerEurekaNodes peerEurekaNodes,
                               ApplicationInfoManager applicationInfoManager) {
        this.serverConfig = serverConfig;
        this.serverCodecs = serverCodecs;
        this.registry = registry;
        this.peerEurekaNodes = peerEurekaNodes;
        this.applicationInfoManager = applicationInfoManager;
    }

    @PostConstruct
    @Override
    public void initialize() throws Exception {
        logger.info("Initializing ...");
        peerEurekaNodes.start();
        registry.init(peerEurekaNodes);
        logger.info("Initialized");
    }

    @PreDestroy
    @Override
    public void shutdown() throws Exception {
        logger.info("Shutting down ...");
        registry.shutdown();
        peerEurekaNodes.shutdown();
        logger.info("Shut down");
    }

    @Override
    public EurekaServerConfig getServerConfig() {
        return serverConfig;
    }

    @Override
    public PeerEurekaNodes getPeerEurekaNodes() {
        return peerEurekaNodes;
    }

    @Override
    public ServerCodecs getServerCodecs() {
        return serverCodecs;
    }

    @Override
    public PeerAwareInstanceRegistry getRegistry() {
        return registry;
    }

    @Override
    public ApplicationInfoManager getApplicationInfoManager() {
        return applicationInfoManager;
    }

}
