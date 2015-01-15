package com.netflix.eureka2.transport.base;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.transport.MessageConnection;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.transport.base.SampleObject.*;
import static org.mockito.Mockito.*;

/**
 * @author David Liu
 */
public class SelfClosingConnectionTest {

    private static final long LIFECYCLE_DURATION_SEC = 30;

    private final TestScheduler testScheduler = Schedulers.test();
    private final MessageConnection connectionDelegate = mock(MessageConnection.class);

    private SelfClosingConnection selfClosingConnection;
    private final PublishSubject<Void> lifecycleSubject = PublishSubject.create();

    @Before
    public void setUp() throws Exception {
        when(connectionDelegate.submit(anyObject())).thenReturn(Observable.<Void>empty());
        when(connectionDelegate.lifecycleObservable()).thenReturn(lifecycleSubject);

        selfClosingConnection = new SelfClosingConnection(connectionDelegate, LIFECYCLE_DURATION_SEC, testScheduler);
    }

    @Test
    public void testSelfTermination() throws Exception {
        TestSubscriber<Void> lifecycleSubscriber = new TestSubscriber<>();
        selfClosingConnection.lifecycleObservable().subscribe(lifecycleSubscriber);

        selfClosingConnection.submit(CONTENT);
        verify(connectionDelegate, times(1)).submit(CONTENT);

        selfClosingConnection.submitWithAck(CONTENT);
        verify(connectionDelegate, times(1)).submitWithAck(CONTENT);

        selfClosingConnection.submitWithAck(CONTENT, 1000);
        verify(connectionDelegate, times(1)).submitWithAck(CONTENT, 1000);

        lifecycleSubscriber.assertNoErrors();

        // Now force timeout
        testScheduler.advanceTimeBy(LIFECYCLE_DURATION_SEC, TimeUnit.SECONDS);

        verify(connectionDelegate, times(1)).shutdown();
    }
}
