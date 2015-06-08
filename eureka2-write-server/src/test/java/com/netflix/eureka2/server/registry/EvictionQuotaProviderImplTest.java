package com.netflix.eureka2.server.registry;

import java.util.concurrent.atomic.AtomicLong;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.config.WriteServerConfig.WriteServerConfigBuilder;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Subscriber;
import rx.subjects.PublishSubject;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class EvictionQuotaProviderImplTest {

    private static final int ALLOWED_PERCENTAGE_DROP = 80;

    private static final ChangeNotification<InstanceInfo> ADD_NOTIFICATION = new ChangeNotification<>(Kind.Add, SampleInstanceInfo.WebServer.build());

    private final SourcedEurekaRegistry<InstanceInfo> registry = mock(SourcedEurekaRegistry.class);
    private final PublishSubject<ChangeNotification<InstanceInfo>> interestSubject = PublishSubject.create();

    private EvictionQuotaProviderImpl evictionQuotaProvider;

    private final QuotaSubscriber quotaSubscriber = new QuotaSubscriber();

    @Before
    public void setUp() throws Exception {
        when(registry.forInterest(Interests.forFullRegistry())).thenReturn(interestSubject);

        WriteServerConfig config = new WriteServerConfigBuilder()
                .withEvictionAllowedPercentageDrop(ALLOWED_PERCENTAGE_DROP)
                .build();

        evictionQuotaProvider = new EvictionQuotaProviderImpl(registry, config);
        evictionQuotaProvider.quota().subscribe(quotaSubscriber);

        // Emit buffer sentinel to mark end of available registry content
        interestSubject.onNext(ChangeNotification.<InstanceInfo>bufferSentinel());
    }

    @After
    public void tearDown() throws Exception {
        if (evictionQuotaProvider != null) {
            evictionQuotaProvider.shutdown();
        }
    }

    @Test
    public void testEvictionOfOneItem() throws Exception {
        when(registry.size()).thenReturn(10);

        quotaSubscriber.doRequest(1);
        assertThat(quotaSubscriber.getGrantedCount(), is(equalTo(1L)));
    }

    @Test
    public void testDelayedEvictionOfOneItemUntilRegistrySizeIncreases() throws Exception {
        // Consume quota limit
        when(registry.size()).thenReturn(10);
        quotaSubscriber.doRequest(2);
        assertThat(quotaSubscriber.getGrantedCount(), is(equalTo(2L)));

        // Request eviction outside of available quota
        when(registry.size()).thenReturn(8);
        quotaSubscriber.doRequest(1);
        assertThat(quotaSubscriber.getGrantedCount(), is(equalTo(2L)));

        // Now trigger notification caused by registry update
        when(registry.size()).thenReturn(9);
        interestSubject.onNext(ADD_NOTIFICATION);
        assertThat(quotaSubscriber.getGrantedCount(), is(equalTo(3L)));
    }

    @Test
    public void testEvictionStateIsResetWhenRegistrySizeIsRestored() throws Exception {
        // Trigger eviction
        when(registry.size()).thenReturn(10);
        quotaSubscriber.doRequest(2);
        assertThat(quotaSubscriber.getGrantedCount(), is(equalTo(2L)));

        // Trigger quota evaluation. We keep current registry size == 10, as if there were no evictions
        interestSubject.onNext(ADD_NOTIFICATION);
        assertThat(quotaSubscriber.getGrantedCount(), is(equalTo(2L)));

        // Now when old eviction state is discarded, shrink registry and repeat eviction test
        when(registry.size()).thenReturn(5);
        quotaSubscriber.doRequest(1);
        assertThat(quotaSubscriber.getGrantedCount(), is(equalTo(3L)));
    }

    static class QuotaSubscriber extends Subscriber<Long> {

        private final AtomicLong granted = new AtomicLong();

        long getGrantedCount() {
            return granted.get();
        }

        void doRequest(long n) {
            request(n);
        }

        @Override
        public void onStart() {
            request(0);
        }

        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable e) {

        }

        @Override
        public void onNext(Long value) {
            granted.addAndGet(value);
        }
    }
}