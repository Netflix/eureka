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

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka.CurrentRequestVersion;
import com.netflix.eureka.InstanceRegistry;
import com.netflix.eureka.PeerAwareInstanceRegistry;
import com.netflix.eureka.Version;

/**
 * A <em>jersey</em> resource that gets information about a particular instance.
 * 
 * @author Karthik Ranganathan, Greg Kim
 *
 */
@Produces({ "application/xml", "application/json" })
@Path("/{version}/instances")
public class InstancesResource {
    private static final Logger logger = LoggerFactory
            .getLogger(InstancesResource.class);

    private final InstanceRegistry registry = PeerAwareInstanceRegistry
            .getInstance();

    @GET
    @Path("{id}")
    public Response getById(@PathParam("version") String version,
            @PathParam("id") String id) {
        CurrentRequestVersion.set(Version.toEnum(version));
        List<InstanceInfo> list = registry.getInstancesById(id);
        if (list != null && list.size() > 0) {
            return Response.ok(list.get(0)).build();
        } else {
            logger.info("Not Found: " + id);
            return Response.status(Status.NOT_FOUND).build();
        }
    }
}
