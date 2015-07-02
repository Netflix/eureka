package com.netflix.eureka2.server.service.selfinfo;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.service.selfinfo.CachingSelfInfoResolver;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Test;
import rx.Observable;
import rx.functions.Action0;
import rx.observers.TestSubscriber;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

/**
 * @author David Liu
 */
public class CachingSelfInfoResolverTest {

    private final InstanceInfo info = SampleInstanceInfo.DiscoveryServer.build();
    private final AtomicInteger subscriberCount = new AtomicInteger();
    private final Observable<InstanceInfo> observable =
            Observable.just(info)
                    .mergeWith(Observable.<InstanceInfo>never())
                    .doOnSubscribe(new Action0() {
                        @Override
                        public void call() {
                            subscriberCount.incrementAndGet();
                        }
                    })
                    .doOnUnsubscribe(new Action0() {
                        @Override
                        public void call() {
                            subscriberCount.decrementAndGet();
                        }
                    });

    @Test
    public void testMultipleSubscribers() {
        TestResolver delegate = spy(new TestResolver());
        CachingSelfInfoResolver resolver = spy(new CachingSelfInfoResolver(delegate));

        assertThat(subscriberCount.get(), is(0));

        TestSubscriber<InstanceInfo> subscriber1 = new TestSubscriber<>();
        resolver.resolve().subscribe(subscriber1);
        assertThat(subscriberCount.get(), is(1));
        verify(delegate, times(1)).resolve();
        verify(resolver, times(1)).resolve();
        subscriber1.assertReceivedOnNext(Arrays.asList(info));

        TestSubscriber<InstanceInfo> subscriber2 = new TestSubscriber<>();
        resolver.resolve().subscribe(subscriber2);
        assertThat(subscriberCount.get(), is(1));
        verify(delegate, times(1)).resolve();
        verify(resolver, times(2)).resolve();
        subscriber2.assertReceivedOnNext(Arrays.asList(info));

        TestSubscriber<InstanceInfo> subscriber3 = new TestSubscriber<>();
        resolver.resolve().subscribe(subscriber3);
        assertThat(subscriberCount.get(), is(1));
        verify(delegate, times(1)).resolve();
        verify(resolver, times(3)).resolve();
        subscriber3.assertReceivedOnNext(Arrays.asList(info));

        subscriber1.unsubscribe();
        assertThat(subscriberCount.get(), is(1));
        subscriber2.unsubscribe();
        assertThat(subscriberCount.get(), is(1));
        subscriber3.unsubscribe();
        assertThat(subscriberCount.get(), is(0));
    }


    private class TestResolver implements SelfInfoResolver {
        @Override
        public Observable<InstanceInfo> resolve() {
            return observable;
        }
    }

}
