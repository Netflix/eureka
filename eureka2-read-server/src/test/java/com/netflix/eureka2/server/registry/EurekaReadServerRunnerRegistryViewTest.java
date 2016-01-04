package com.netflix.eureka2.server.registry;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import rx.subjects.PublishSubject;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TODO: read registry view contract has changed. Are these tests still valid/should the contract change back?
 *
 * @author Tomasz Bak
 */
@Ignore
public class EurekaReadServerRunnerRegistryViewTest {

    private static final Interest<InstanceInfo> INTEREST = Interests.forVips("testVip");
    private static final ChangeNotification<InstanceInfo> BUFFER_START = StreamStateNotification.bufferStartNotification(INTEREST);
    private static final ChangeNotification<InstanceInfo> BUFFER_END = StreamStateNotification.bufferEndNotification(INTEREST);

    private static final ChangeNotification<InstanceInfo> ADD_INSTANCE_1 = new ChangeNotification<>(Kind.Add, SampleInstanceInfo.EurekaWriteServer.build());
    private static final ChangeNotification<InstanceInfo> ADD_INSTANCE_2 = new ChangeNotification<>(Kind.Add, SampleInstanceInfo.EurekaWriteServer.build());

    private final EurekaInterestClient interestClient = mock(EurekaInterestClient.class);
    private final PublishSubject<ChangeNotification<InstanceInfo>> interestSubject = PublishSubject.create();

    private final EurekaReadServerRegistryView registry = new EurekaReadServerRegistryView(interestClient);

    private final ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

    @Before
    public void setUp() throws Exception {
        when(interestClient.forInterest(any(Interest.class))).thenReturn(interestSubject);
        registry.forInterest(INTEREST).subscribe(testSubscriber);
    }

    @Test
    public void testVoidBufferSentinelsAreIgnored() throws Exception {
        // Buffer sentinel with no data ahead of it shall be swallowed
        interestSubject.onNext(ChangeNotification.<InstanceInfo>bufferSentinel());
        assertThat(testSubscriber.takeNext(), is(nullValue()));
    }

    @Test
    public void testBufferSentinelsAfterSingleItemDoNotGenerateBufferStartEndMarkers() throws Exception {
        // Buffer sentinel after single item doesn't generate buffer delineation
        interestSubject.onNext(ADD_INSTANCE_1);
        interestSubject.onNext(ChangeNotification.<InstanceInfo>bufferSentinel());
        assertThat(testSubscriber.takeNext(), is(ADD_INSTANCE_1));
        assertThat(testSubscriber.takeNext(), is(nullValue()));
    }

    @Test
    public void testBufferSentinelsAreTransformedToBufferStartEndMarkers() throws Exception {
        // Buffer sentinel after two or more items creates delineation markers
        interestSubject.onNext(ADD_INSTANCE_1);
        interestSubject.onNext(ADD_INSTANCE_2);
        interestSubject.onNext(ChangeNotification.<InstanceInfo>bufferSentinel());

        assertThat(testSubscriber.takeNext(), is(equalTo(BUFFER_START)));
        assertThat(testSubscriber.takeNext(), is(equalTo(ADD_INSTANCE_1)));
        assertThat(testSubscriber.takeNext(), is(equalTo(ADD_INSTANCE_2)));
        assertThat(testSubscriber.takeNext(), is(equalTo(BUFFER_END)));
    }
}