package com.netflix.eureka2.server.channel;

import java.util.Collections;

import com.netflix.eureka2.protocol.replication.RegisterCopy;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.protocol.replication.UnregisterCopy;
import com.netflix.eureka2.protocol.replication.UpdateCopy;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class SenderReplicationChannelTest extends AbstractReplicationChannelTest {

    private final TransportClient transportClient = mock(TransportClient.class);
    private final MessageConnection connection = mock(MessageConnection.class);
    private final ReplaySubject<Void> connectionLifeCycle = ReplaySubject.create();

    private SenderReplicationChannel replicationChannel;
    private final PublishSubject<Object> connectionIncoming = PublishSubject.create();

    @Before
    public void setUp() throws Exception {
        when(transportClient.connect()).thenReturn(Observable.just(connection));
        when(connection.lifecycleObservable()).thenReturn(connectionLifeCycle);
        when(connection.incoming()).thenReturn(connectionIncoming);

        replicationChannel = new SenderReplicationChannel(transportClient);
    }

    @Test
    public void testHandshake() throws Exception {
        TestSubscriber<ReplicationHelloReply> replySubscriber = new TestSubscriber<>();

        // Send hello
        when(connection.submit(HELLO)).thenReturn(Observable.<Void>empty());
        replicationChannel.hello(HELLO).subscribe(replySubscriber);

        replySubscriber.assertNoErrors();
        verify(connection, times(1)).submit(HELLO);

        // Reply with handshake
        connectionIncoming.onNext(HELLO_REPLY);

        replySubscriber.assertReceivedOnNext(Collections.singletonList(HELLO_REPLY));
    }

    @Test
    public void testRegistration() throws Exception {
        TestSubscriber<Void> replySubscriber = new TestSubscriber<>();

        RegisterCopy message = new RegisterCopy(APP_INFO);
        when(connection.submit(message)).thenReturn(Observable.<Void>empty());
        replicationChannel.register(APP_INFO).subscribe(replySubscriber);

        replySubscriber.assertNoErrors();
        replySubscriber.assertTerminalEvent();
        verify(connection, times(1)).submit(message);
    }

    @Test
    public void testUpdate() throws Exception {
        TestSubscriber<Void> replySubscriber = new TestSubscriber<>();

        UpdateCopy message = new UpdateCopy(APP_INFO);
        when(connection.submit(message)).thenReturn(Observable.<Void>empty());
        replicationChannel.update(APP_INFO).subscribe(replySubscriber);

        replySubscriber.assertNoErrors();
        replySubscriber.assertTerminalEvent();
        verify(connection, times(1)).submit(message);
    }

    @Test
    public void testUnregister() throws Exception {
        TestSubscriber<Void> replySubscriber = new TestSubscriber<>();

        UnregisterCopy message = new UnregisterCopy(APP_INFO.getId());
        when(connection.submit(message)).thenReturn(Observable.<Void>empty());
        replicationChannel.unregister(APP_INFO.getId()).subscribe(replySubscriber);

        replySubscriber.assertNoErrors();
        replySubscriber.assertTerminalEvent();
        verify(connection, times(1)).submit(message);
    }
}