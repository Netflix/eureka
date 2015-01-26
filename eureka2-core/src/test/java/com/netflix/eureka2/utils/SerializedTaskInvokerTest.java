package com.netflix.eureka2.utils;

import java.util.concurrent.Callable;

import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * TODO
 * @author David Liu
 */
public class SerializedTaskInvokerTest {

    private final SerializedTaskInvokerMetrics metrics = mock(SerializedTaskInvokerMetrics.class);

    private final TestScheduler testScheduler = Schedulers.test();
    private SerializedTaskInvoker invoker;

    @Before
    public void setUp() {
        invoker = new TestInvoker();
    }

    @Test
    public void testSyncTasksExecutedInOrder() {
        invoker.submitForAck(new SyncAckTask(true));
    }

    public void testAsyncTasksExecutedInOrder() {
    }

    @Test
    public void testMetrics() throws Exception {
        // Successful task
        TestSubscriber<Void> testSubscriber = new TestSubscriber<>();
        invoker.submitForAck(new SyncAckTask(true)).subscribe(testSubscriber);

        verify(metrics, times(1)).setQueueSize(1);
        testScheduler.triggerActions();
        testSubscriber.awaitTerminalEvent();
        verify(metrics, times(1)).setQueueSize(0);

        verify(metrics, times(1)).incrementInputSuccess();
        verify(metrics, times(1)).incrementOutputSuccess();
        reset(metrics);

        // Successful task
        testSubscriber = new TestSubscriber<>();
        invoker.submitForAck(new SyncAckTask(false)).subscribe(testSubscriber);

        verify(metrics, times(1)).setQueueSize(1);
        testScheduler.triggerActions();
        testSubscriber.awaitTerminalEvent();
        verify(metrics, times(1)).setQueueSize(0);

        testScheduler.triggerActions();

        verify(metrics, times(1)).incrementInputSuccess();
        verify(metrics, times(1)).incrementOutputFailure();
    }

    static class SyncAckTask implements Callable<Observable<Void>> {

        private final boolean success;

        SyncAckTask(boolean success) {
            this.success = success;
        }

        @Override
        public Observable<Void> call() throws Exception {
            if (!success) {
                throw new RuntimeException("task error");
            }
            return Observable.create(new Observable.OnSubscribe<Void>() {
                @Override
                public void call(Subscriber<? super Void> subscriber) {
                    subscriber.onCompleted();
                }
            });
        }
    }

    class TestInvoker extends SerializedTaskInvoker {
        TestInvoker() {
            super(metrics, testScheduler);
        }
    }
}
