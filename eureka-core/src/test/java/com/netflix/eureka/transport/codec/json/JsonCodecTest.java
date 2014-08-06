package com.netflix.eureka.transport.codec.json;

import com.netflix.eureka.transport.Acknowledgement;
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
        EmbeddedChannel ch = new EmbeddedChannel(new JsonCodec(SampleUserObject.TRANSPORT_MODEL));

        SampleUserObject message = new SampleUserObject("stringValue", 123);
        assertTrue("Message should be written successfuly to the channel", ch.writeOutbound(message));

        ch.writeInbound(ch.readOutbound());
        Object received = ch.readInbound();

        assertEquals(message, received);
    }

    @Test
    public void testUserContentWithAck() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonCodec(SampleUserObject.TRANSPORT_MODEL));

        Object message = new SampleUserObject("stringValue", 123);
        assertTrue("Message should be written successfuly to the channel", ch.writeOutbound(message));

        ch.writeInbound(ch.readOutbound());
        Object received = ch.readInbound();

        assertEquals(message, received);
    }

    @Test
    public void testAcknowledgement() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonCodec(SampleUserObject.TRANSPORT_MODEL));

        Acknowledgement message = new Acknowledgement("cid123");
        assertTrue("Message should be written successfuly to the channel", ch.writeOutbound(message));

        ch.writeInbound(ch.readOutbound());
        Acknowledgement received = (Acknowledgement) ch.readInbound();

        assertEquals(message.getCorrelationId(), received.getCorrelationId());
    }
}