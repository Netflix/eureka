package com.netflix.eureka2.channel;

import com.netflix.eureka2.client.channel.RegistrationChannelImpl;
import com.netflix.eureka2.client.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author David Liu
 */
public class AbstractClientChannelTest {

    private MessageConnection messageConnection1;
    private MessageConnection messageConnection2;

    private AbstractClientChannel channel;

    @Before
    public void setUp() {
        messageConnection1 = mock(MessageConnection.class);
        messageConnection2 = mock(MessageConnection.class);
        List<MessageConnection> messageConnections = Arrays.asList(messageConnection1, messageConnection2);

        TransportClient transportClient = mock(TransportClient.class);
        when(transportClient.connect()).thenReturn(Observable.from(messageConnections));

        channel = new RegistrationChannelImpl(transportClient, mock(RegistrationChannelMetrics.class));
    }

    @Test
    public void testMultipleConnectReturnSameConnection() {
        MessageConnection firstConnection = (MessageConnection) channel.connect().toBlocking().firstOrDefault(null);
        // call again
        MessageConnection secondConnection = (MessageConnection) channel.connect().toBlocking().firstOrDefault(null);

        assertEquals(messageConnection1, firstConnection);
        assertEquals(messageConnection1, secondConnection);
        assertNotEquals(messageConnection2, firstConnection);
        assertNotEquals(messageConnection2, secondConnection);
    }
}
