package com.netflix.template.server;

import com.netflix.template.common.Conversation;
import com.netflix.template.common.Sentence;

import javax.ws.rs.GET;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Simple server to have a conversation.
 */
@Path("/talk")
public class TalkServer implements Conversation {

    /**
     * {@inheritDoc}
     */
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public Sentence greeting() {
        return new Sentence("Hello");
    }

    /**
     * {@inheritDoc}
     */
    @DELETE
    @Produces(MediaType.APPLICATION_XML)
    public Sentence farewell() {
        return new Sentence("Goodbye");
    }
}

