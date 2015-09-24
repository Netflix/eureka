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

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.EurekaServerContextHolder;
import com.netflix.eureka.util.StatusInfo;
import com.netflix.eureka.util.StatusUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An utility class for exposing information about peer nodes.
 *
 * @author Karthik Ranganathan, Greg Kim
 */
@Path("/{version}/status")
@Produces({"application/xml", "application/json"})
public class StatusResource {
    private static final Logger logger = LoggerFactory.getLogger(StatusResource.class);
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss Z";

    private final StatusUtil statusUtil;

    @Inject
    StatusResource(EurekaServerContext server) {
        this.statusUtil = new StatusUtil(server);
    }

    public StatusResource() {
        this(EurekaServerContextHolder.getInstance().getServerContext());
    }

    @GET
    public StatusInfo getStatusInfo() {
        return statusUtil.getStatusInfo();
    }

    public static String getCurrentTimeAsString() {
        SimpleDateFormat format = new SimpleDateFormat(DATE_FORMAT);
        return format.format(new Date());
    }
}
