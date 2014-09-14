package com.netflix.eureka.client;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.interests.Interests;
import com.netflix.eureka.interests.SampleChangeNotification;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.InterestChannel;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;
import rx.Subscriber;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author David Liu
 */
@RunWith(MockitoJUnitRunner.class)
public class InterestProcessorTest {

    @Mock
    protected InterestChannel interestChannel;

    protected Interest<InstanceInfo> interest;
    protected InterestProcessor processor;

    @Rule
    public final ExternalResource testResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            interest = Interests.forVip("abc");

            Observable<ChangeNotification<InstanceInfo>> mockInterestStream = Observable.from(Arrays.asList(
                    SampleChangeNotification.DiscoveryAdd.newNotification(),
                    SampleChangeNotification.DiscoveryAdd.newNotification(),
                    SampleChangeNotification.DiscoveryAdd.newNotification()
            ));

            when(interestChannel.register(eq(interest))).thenReturn(mockInterestStream);
            when(interestChannel.upgrade(eq(interest))).thenReturn(Observable.<Void>empty());

            processor = new InterestProcessor(interestChannel);
        }

        @Override
        protected void after() {
            processor.shutdown();
        }
    };



    @Test
    public void testForInterestOnlyRegisterFirstTime() throws Exception {

        // first forInterest
        final CountDownLatch latch1 = new CountDownLatch(1);
        processor.forInterest(interest).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                latch1.countDown();
            }

            @Override
            public void onError(Throwable e) {
                Assert.fail("Should not onError");
            }

            @Override
            public void onNext(Void aVoid) {
                Assert.fail("Should not onNext");
            }
        });

        assertThat(latch1.await(1, TimeUnit.MINUTES), equalTo(true));

        verify(interestChannel, times(1)).register(interest);
        verify(interestChannel, times(0)).upgrade(interest);


        // second forInterest
        final CountDownLatch latch2 = new CountDownLatch(1);
        processor.forInterest(interest).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                latch2.countDown();
            }

            @Override
            public void onError(Throwable e) {
                Assert.fail("Should not onError");
            }

            @Override
            public void onNext(Void aVoid) {
                Assert.fail("Should not onNext");
            }
        });

        assertThat(latch2.await(1, TimeUnit.MINUTES), equalTo(true));

        verify(interestChannel, times(1)).register(interest);
        verify(interestChannel, times(1)).upgrade(interest);


        // third forInterest
        final CountDownLatch latch3 = new CountDownLatch(1);
        processor.forInterest(interest).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                latch3.countDown();
            }

            @Override
            public void onError(Throwable e) {
                Assert.fail("Should not onError");
            }

            @Override
            public void onNext(Void aVoid) {
                Assert.fail("Should not onNext");
            }
        });

        assertThat(latch3.await(1, TimeUnit.MINUTES), equalTo(true));

        verify(interestChannel, times(1)).register(interest);
        verify(interestChannel, times(2)).upgrade(interest);
    }
}
