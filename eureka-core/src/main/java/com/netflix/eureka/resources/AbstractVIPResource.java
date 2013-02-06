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

import com.netflix.eureka.CurrentRequestVersion;
import com.netflix.eureka.PeerAwareInstanceRegistry;
import com.netflix.eureka.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;

/**
 * Abstract class for the common functionality of a VIP/SVIP resource.
 *
 * @author Nitesh Kant (nkant@netflix.com)
 */
abstract class AbstractVIPResource {

    private static final Logger logger = LoggerFactory.getLogger(AbstractVIPResource.class);

    protected Response getVipResponse(String version, String entityName, String acceptHeader,
                                      ResponseCache.Key.EntityType entityType) {
        if (!PeerAwareInstanceRegistry.getInstance().shouldAllowAccess()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        CurrentRequestVersion.set(Version.toEnum(version));
        ResponseCache.KeyType keyType = ResponseCache.KeyType.JSON;
        if (acceptHeader == null || !acceptHeader.contains("json")) {
            keyType = ResponseCache.KeyType.XML;
        }

        ResponseCache.Key cacheKey = new ResponseCache.Key(entityType, entityName, keyType, CurrentRequestVersion.get());

        String payLoad = ResponseCache.getInstance().get(cacheKey);

        if (payLoad != null) {
            logger.debug("Found: {}", entityName);
            return Response.ok(payLoad).build();
        } else {
            logger.debug("Not Found: {}", entityName);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
