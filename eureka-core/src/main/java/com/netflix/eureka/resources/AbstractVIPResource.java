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

package com.netflix.eureka.resources;

import javax.ws.rs.core.Response;

import com.netflix.appinfo.EurekaAccept;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.EurekaServerContextHolder;
import com.netflix.eureka.Version;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.registry.ResponseCache;
import com.netflix.eureka.registry.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class for the common functionality of a VIP/SVIP resource.
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
abstract class AbstractVIPResource {

    private static final Logger logger = LoggerFactory.getLogger(AbstractVIPResource.class);

    private final PeerAwareInstanceRegistry registry;
    private final ResponseCache responseCache;

    AbstractVIPResource(EurekaServerContext server) {
        this.registry = server.getRegistry();
        this.responseCache = registry.getResponseCache();
    }

    AbstractVIPResource() {
        this(EurekaServerContextHolder.getInstance().getServerContext());
    }

    protected Response getVipResponse(String version, String entityName, String acceptHeader,
                                      EurekaAccept eurekaAccept, Key.EntityType entityType) {
        if (!registry.shouldAllowAccess(false)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        CurrentRequestVersion.set(Version.toEnum(version));
        Key.KeyType keyType = Key.KeyType.JSON;
        if (acceptHeader == null || !acceptHeader.contains("json")) {
            keyType = Key.KeyType.XML;
        }

        Key cacheKey = new Key(
                entityType,
                entityName,
                keyType,
                CurrentRequestVersion.get(),
                eurekaAccept
        );

        String payLoad = responseCache.get(cacheKey);

        if (payLoad != null) {
            logger.debug("Found: {}", entityName);
            return Response.ok(payLoad).build();
        } else {
            logger.debug("Not Found: {}", entityName);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
