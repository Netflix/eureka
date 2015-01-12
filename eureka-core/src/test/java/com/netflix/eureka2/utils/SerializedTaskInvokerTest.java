package com.netflix.eureka2.utils;

import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * TODO
 * @author David Liu
 */
public class SerializedTaskInvokerTest {

    private SerializedTaskInvoker invoker;

    private volatile Queue<Integer> taskInitQueue;
    private volatile Queue<Integer> taskCompletionQueue;

    @Before
    public void setUp() {
        invoker = new TestInvoker();

        taskInitQueue = new ConcurrentLinkedQueue<>();
        taskCompletionQueue = new ConcurrentLinkedQueue<>();
    }

    @Test
    public void testSyncTasksExecutedInOrder() {
        invoker.submitForAck(new SyncAckTask());
    }

    public void testAsyncTasksExecutedInOrder() {

    }


    static class SyncAckTask implements Callable<Observable<Void>> {

        @Override
        public Observable<Void> call() throws Exception {
            return Observable.create(new Observable.OnSubscribe<Void>() {
                @Override
                public void call(Subscriber<? super Void> subscriber) {

                }
            });
        }
    }


    static class TestInvoker extends SerializedTaskInvoker {

    }

}
