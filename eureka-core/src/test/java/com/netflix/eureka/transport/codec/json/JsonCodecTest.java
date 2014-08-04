package com.netflix.eureka.transport.codec.json;

import com.netflix.eureka.transport.Acknowledgement;
import com.netflix.eureka.transport.UserContent;
import com.netflix.eureka.transport.UserContentWithAck;
import com.netflix.eureka.transport.base.SampleUserObject;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class JsonCodecTest {

    @Test
    public void testUserContent() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonCodec());

        UserContent message = new UserContent(new SampleUserObject("stringValue", 123));
        assertTrue("Message should be written successfuly to the channel", ch.writeOutbound(message));

        ch.writeInbound(ch.readOutbound());
        UserContent received = (UserContent) ch.readInbound();

        assertEquals(message.getContent(), received.getContent());
    }

    @Test
    public void testUserContentWithAck() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonCodec());

        UserContentWithAck message = new UserContentWithAck(new SampleUserObject("stringValue", 123), "cid123", 100);
        assertTrue("Message should be written successfuly to the channel", ch.writeOutbound(message));

        ch.writeInbound(ch.readOutbound());
        UserContentWithAck received = (UserContentWithAck) ch.readInbound();

        assertEquals(message.getCorrelationId(), received.getCorrelationId());
        assertEquals(message.getTimeout(), received.getTimeout());
        assertEquals(message.getContent(), received.getContent());
    }

    @Test
    public void testAcknowledgement() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonCodec());

        Acknowledgement message = new Acknowledgement("cid123");
        assertTrue("Message should be written successfuly to the channel", ch.writeOutbound(message));

        ch.writeInbound(ch.readOutbound());
        Acknowledgement received = (Acknowledgement) ch.readInbound();

        assertEquals(message.getCorrelationId(), received.getCorrelationId());
    }
}