package com.netflix.eureka2.channel;

import com.netflix.eureka2.client.channel.RegistrationChannelImpl;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.spi.transport.EurekaConnection;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Observable;
import rx.observers.TestSubscriber;
import rx.subjects.ReplaySubject;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author David Liu
 */
public class AbstractClientChannelTest {

    private TestSubscriber<Void> testSubscriber;

    private EurekaConnection messageConnection1;
    private EurekaConnection messageConnection2;

    private AbstractClientChannel channel;

    @Before
    public void setUp() {
        final ReplaySubject<Void> connection1Lifecycle = ReplaySubject.create();
        final ReplaySubject<Void> connection2Lifecycle = ReplaySubject.create();

        testSubscriber = new TestSubscriber<>();

        messageConnection1 = mock(EurekaConnection.class);
        messageConnection2 = mock(EurekaConnection.class);
        when(messageConnection1.lifecycleObservable()).thenReturn(connection1Lifecycle);
        when(messageConnection2.lifecycleObservable()).thenReturn(connection2Lifecycle);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                connection1Lifecycle.onCompleted();
                return null;
            }
        }).when(messageConnection1).shutdown();
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                connection1Lifecycle.onError(new Exception("test: error"));
                return null;
            }
        }).when(messageConnection1).shutdown(any(Throwable.class));

        List<EurekaConnection> messageConnections = Arrays.asList(messageConnection1, messageConnection2);

        TransportClient transportClient = mock(TransportClient.class);
        when(transportClient.connect()).thenReturn(Observable.from(messageConnections));

        channel = new RegistrationChannelImpl(transportClient, mock(RegistrationChannelMetrics.class));
    }

    @Test(timeout = 60000)
    public void testMultipleConnectReturnSameConnection() {
        EurekaConnection firstConnection = (EurekaConnection) channel.connect().toBlocking().firstOrDefault(null);
        // call again
        EurekaConnection secondConnection = (EurekaConnection) channel.connect().toBlocking().firstOrDefault(null);

        assertEquals(messageConnection1, firstConnection);
        assertEquals(messageConnection1, secondConnection);
        assertNotEquals(messageConnection2, firstConnection);
        assertNotEquals(messageConnection2, secondConnection);
    }

    @Test
    public void testOnCompleteOfUnderlyingConnectionOnCompleteChannel() {
        EurekaConnection connection = (EurekaConnection) channel.connect().toBlocking().firstOrDefault(null);
        channel.asLifecycleObservable().subscribe(testSubscriber);
        connection.shutdown();
        testSubscriber.assertTerminalEvent();
        testSubscriber.assertNoErrors();
    }

    @Test
    public void testOnErrorOfUnderlyingConnectionOnErrorChannel() {
        EurekaConnection connection = (EurekaConnection) channel.connect().toBlocking().firstOrDefault(null);
        channel.asLifecycleObservable().subscribe(testSubscriber);
        connection.shutdown(new Exception("test: exception"));
        testSubscriber.assertTerminalEvent();
        assertEquals(1, testSubscriber.getOnErrorEvents().size());
    }
}
