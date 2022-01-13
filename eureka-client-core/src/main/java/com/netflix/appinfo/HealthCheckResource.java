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

package com.netflix.appinfo;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A basic <em>healthcheck</em> jersey resource.
 *
 * This can be used a {@link HealthCheckCallback} resource if required.
 * @author Karthik Ranganathan, Greg Kim
 *
 */
@Path("/healthcheck")
public class HealthCheckResource {
    private static final Logger s_logger = LoggerFactory
            .getLogger(HealthCheckResource.class);

    @GET
    public Response doHealthCheck() {
        try {
            InstanceInfo myInfo = ApplicationInfoManager.getInstance()
                    .getInfo();

            switch (myInfo.getStatus()) {
                case UP:
                    // Return status 200
                    return Response.status(Status.OK).build();
                case STARTING:
                    // Return status 204
                    return Response.status(Status.NO_CONTENT).build();
                case OUT_OF_SERVICE:
                    // Return 503
                    return Response.status(Status.SERVICE_UNAVAILABLE).build();
                default:
                    // Return status 500
                    return Response.status(Status.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Throwable th) {
            s_logger.error("Error doing healthceck", th);
            // Return status 500
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }
}
