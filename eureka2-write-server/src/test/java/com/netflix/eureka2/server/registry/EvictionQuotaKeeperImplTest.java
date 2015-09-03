package com.netflix.eureka2.server.registry;

import java.util.concurrent.atomic.AtomicLong;

import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.instance.InstanceInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Subscriber;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class EvictionQuotaKeeperImplTest {

    private static final int ALLOWED_PERCENTAGE_DROP = 20;

    private final EurekaRegistryConfig config = mock(EurekaRegistryConfig.class);
    private final EurekaRegistrationProcessor<InstanceInfo> registrationProcessor = mock(EurekaRegistrationProcessor.class);
    private final PublishSubject<ChangeNotification<InstanceInfo>> interestSubject = PublishSubject.create();
    private final QuotaSubscriber quotaSubscriber = new QuotaSubscriber();
    private final BehaviorSubject<Integer> sizeSubject = BehaviorSubject.create();

    private EvictionQuotaKeeperImpl evictionQuotaProvider;

    @Before
    public void setUp() throws Exception {
        when(registrationProcessor.sizeObservable()).thenReturn(sizeSubject);
        when(config.getEvictionAllowedPercentageDrop()).thenReturn(ALLOWED_PERCENTAGE_DROP);

        evictionQuotaProvider = new EvictionQuotaKeeperImpl(registrationProcessor, config);
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
        setSize(10);

        quotaSubscriber.doRequest(1);
        assertThat(quotaSubscriber.getGrantedCount(), is(equalTo(1L)));
    }

    @Test
    public void testDelayedEvictionOfOneItemUntilRegistrySizeIncreases() throws Exception {
        // Consume quota limit
        setSize(10);
        quotaSubscriber.doRequest(2);
        assertThat(quotaSubscriber.getGrantedCount(), is(equalTo(2L)));

        // Request eviction outside of available quota
        // note that we don't onNext the sizeSubject here to not trigger permit re-evaluations
        when(registrationProcessor.size()).thenReturn(8);
        quotaSubscriber.doRequest(1);
        assertThat(quotaSubscriber.getGrantedCount(), is(equalTo(2L)));

        // Now trigger notification caused by registry update
        setSize(9);
        assertThat(quotaSubscriber.getGrantedCount(), is(equalTo(3L)));
    }

    @Test
    public void testEvictionStatePolicy() {
        EvictionQuotaKeeperImpl.EvictionState evictionState =
                new EvictionQuotaKeeperImpl(registrationProcessor, config).new EvictionState(10);

        // good to evict
        when(config.getEvictionAllowedPercentageDrop()).thenReturn(20);  // 20% eviction
        when(registrationProcessor.size()).thenReturn(10);
        assertThat(evictionState.isEvictionAllowed(), is(true));

        // configured to not evict at all
        when(config.getEvictionAllowedPercentageDrop()).thenReturn(0);  // 0 eviction
        when(registrationProcessor.size()).thenReturn(10);
        assertThat(evictionState.isEvictionAllowed(), is(false));

        // curr size too low to evict
        when(config.getEvictionAllowedPercentageDrop()).thenReturn(20);  // 20% eviction
        when(registrationProcessor.size()).thenReturn(7);
        assertThat(evictionState.isEvictionAllowed(), is(false));

        // unary size test
        evictionState =
                new EvictionQuotaKeeperImpl(registrationProcessor, config).new EvictionState(1);
        when(config.getEvictionAllowedPercentageDrop()).thenReturn(20);  // 20% eviction
        when(registrationProcessor.size()).thenReturn(1);
        assertThat(evictionState.isEvictionAllowed(), is(false));
    }


    private void setSize(int size) {
        when(registrationProcessor.size()).thenReturn(size);
        sizeSubject.onNext(size);
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