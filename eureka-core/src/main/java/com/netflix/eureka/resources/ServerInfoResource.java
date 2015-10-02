package com.netflix.eureka.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.EurekaServerContextHolder;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.Map;

/**
 * @author David Liu
 */
@Produces("application/json")
@Path("/serverinfo")
public class ServerInfoResource {
    private final PeerAwareInstanceRegistry registry;

    @Inject
    ServerInfoResource(EurekaServerContext server) {
        this.registry = server.getRegistry();
    }

    public ServerInfoResource() {
        this(EurekaServerContextHolder.getInstance().getServerContext());
    }

    @GET
    @Path("statusoverrides")
    public Response getOverrides() throws Exception {
        Map<String, InstanceInfo.InstanceStatus> result = registry.overriddenInstanceStatusesSnapshot();

        ObjectMapper objectMapper = new ObjectMapper();
        String responseStr = objectMapper.writeValueAsString(result);
        return Response.ok(responseStr).build();
    }
}
