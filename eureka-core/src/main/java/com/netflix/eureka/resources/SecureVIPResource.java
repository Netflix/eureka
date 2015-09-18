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

import com.netflix.appinfo.EurekaAccept;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.EurekaServerContextHolder;
import com.netflix.eureka.registry.Key;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

/**
 * A <em>jersey</em> resource for retrieving all instances with a given secure VIP address.
 *
 * @author Karthik Ranganathan
 *
 */
@Path("/{version}/svips")
@Produces({"application/xml", "application/json"})
public class SecureVIPResource extends AbstractVIPResource {

    @Inject
    SecureVIPResource(EurekaServerContext server) {
        super(server);
    }

    public SecureVIPResource() {
        this(EurekaServerContextHolder.getInstance().getServerContext());
    }

    @GET
    @Path("{svipAddress}")
    public Response statusUpdate(@PathParam("version") String version,
                                 @PathParam("svipAddress") String svipAddress,
                                 @HeaderParam("Accept") final String acceptHeader,
                                 @HeaderParam(EurekaAccept.HTTP_X_EUREKA_ACCEPT) String eurekaAccept) {
        return getVipResponse(version, svipAddress, acceptHeader,
                EurekaAccept.fromString(eurekaAccept), Key.EntityType.SVIP);
    }

}
