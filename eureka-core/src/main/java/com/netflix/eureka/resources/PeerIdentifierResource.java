package com.netflix.eureka.resources;

import com.netflix.eureka.cluster.PeerEurekaNodes;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path("/{version}/peerIdentifier")
public class PeerIdentifierResource {

    @GET
    @Produces(TEXT_PLAIN)
    public String getPeerId() {
        return PeerEurekaNodes.thisNodeId;
    }
}
