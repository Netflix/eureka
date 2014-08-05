package com.netflix.eureka.transport.codec.avro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.netflix.eureka.transport.UserContent;
import com.netflix.eureka.transport.UserContentWithAck;
import com.netflix.eureka.transport.base.SampleUserObject;
import io.netty.channel.embedded.EmbeddedChannel;
import org.apache.avro.Schema;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class AvroCodecTest {

    private static Schema schema;

    @BeforeClass
    public static void setUpClasss() throws Exception {
        List<Class<?>> types = new ArrayList<Class<?>>();
        types.add(SampleUserObject.class);
        schema = MessageBrokerSchema.brokerSchemaFrom(SampleUserObject.TRANSPORT_MODEL);
    }

    @Test
    public void testEncodeDecode() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new AvroCodec(schema));

        UserContent message = new UserContentWithAck(new SampleUserObject("stringValue", 123), "id123", 0);
        assertTrue("Message should be written successfuly to the channel", ch.writeOutbound(message));

        ch.writeInbound(ch.readOutbound());
        Object received = ch.readInbound();
        assertTrue("Expected instance of UserContent", received instanceof UserContentWithAck);
        assertEquals("Encoded/decoded shall produce identical object", message.getContent(), ((UserContentWithAck) received).getContent());
    }

    public static void main(String[] args) throws Exception {
        setUpClasss();
        AvroCodecTest codecTest = new AvroCodecTest();
        long start = System.currentTimeMillis();
        int n = 1000000;
        for (int i = 0; i < n; i++) {
            codecTest.testEncodeDecode();
        }
        System.out.println("Trx/sec: " + (1000 * n) / (System.currentTimeMillis() - start));
    }
}