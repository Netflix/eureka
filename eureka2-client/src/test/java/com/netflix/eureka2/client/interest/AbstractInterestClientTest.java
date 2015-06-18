package com.netflix.eureka2.client.interest;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.channel.InterestChannelImpl;
import com.netflix.eureka2.connection.RetryableConnection;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.subjects.ReplaySubject;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class AbstractInterestClientTest {

    private SourcedEurekaRegistry<InstanceInfo> registry;
    private RetryableConnection<InterestChannel> retryableConnection;
    private ReplaySubject<InterestChannel> channelSubject;

    private MyInterestClient client;

    @Before
    public void setUp() {
        registry = mock(SourcedEurekaRegistry.class);
        retryableConnection = mock(RetryableConnection.class);

        channelSubject = ReplaySubject.create();
        when(retryableConnection.getChannelObservable()).thenReturn(channelSubject);

        client = new MyInterestClient(registry, 0);
    }

    @Test
    public void testRegistryEvictionSubscribe() throws Exception {
        InterestChannelImpl channel1 = mock(InterestChannelImpl.class);
        InterestChannelImpl channel2 = mock(InterestChannelImpl.class);
        when(channel1.getSource()).thenReturn(new Source(Source.Origin.INTERESTED));
        when(channel2.getSource()).thenReturn(new Source(Source.Origin.INTERESTED));

        ReplaySubject<ChangeNotification<InstanceInfo>> channel1BatchSubject = ReplaySubject.create();
        ReplaySubject<ChangeNotification<InstanceInfo>> channel2BatchSubject = ReplaySubject.create();
        when(channel1.getChangeNotificationStream()).thenReturn(channel1BatchSubject);
        when(channel2.getChangeNotificationStream()).thenReturn(channel2BatchSubject);

        final ReplaySubject<Void> channel1Lifecycle = ReplaySubject.create();
        ReplaySubject<Void> channel2Lifecycle = ReplaySubject.create();
        when(channel1.asLifecycleObservable()).thenReturn(channel1Lifecycle);
        when(channel2.asLifecycleObservable()).thenReturn(channel2Lifecycle);

        client.registryEvictionSubscribe(retryableConnection);
        channelSubject.onNext(channel1);

        channel1BatchSubject.onNext(newBufferStart());
        verify(registry, never()).evictAll(any(Source.SourceMatcher.class));

        channel1BatchSubject.onNext(newBufferEnd());
        verify(registry, never()).evictAll(any(Source.SourceMatcher.class));

        channelSubject.onNext(channel2);
        channel1Lifecycle.onError(new Exception("test error"));

        channel2BatchSubject.onNext(newBufferStart());
        verify(registry, never()).evictAll(any(Source.SourceMatcher.class));

        channel2BatchSubject.onNext(newBufferEnd());
        verify(registry, times(1)).evictAll(any(Source.SourceMatcher.class));
    }



    private ChangeNotification<InstanceInfo> newBufferStart() {
        return StreamStateNotification.bufferStartNotification(Interests.forFullRegistry());
    }

    private ChangeNotification<InstanceInfo> newBufferEnd() {
        return StreamStateNotification.bufferEndNotification(Interests.forFullRegistry());
    }

    class MyInterestClient extends AbstractInterestClient {
        protected MyInterestClient(SourcedEurekaRegistry<InstanceInfo> registry, int retryWaitMillis) {
            super(registry, retryWaitMillis);
        }

        @Override
        protected RetryableConnection<InterestChannel> getRetryableConnection() {
            return retryableConnection;
        }

        @Override
        public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
            return null;
        }
    }
}
