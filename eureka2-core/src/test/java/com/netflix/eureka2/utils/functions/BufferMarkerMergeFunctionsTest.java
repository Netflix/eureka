package com.netflix.eureka2.utils.functions;

import java.util.List;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Sourced;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.SourcedStreamStateNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * @author David Liu
 */
public class BufferMarkerMergeFunctionsTest {

    private final TestScheduler testScheduler = Schedulers.test();

    private final Logger logger = NOPLogger.NOP_LOGGER;
    private final BufferMarkerMergeFunctions mergeFunctions = new BufferMarkerMergeFunctions(logger);
    private final ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
    private final PublishSubject<ChangeNotification<InstanceInfo>> testStream = PublishSubject.create();

    @Test
    public void testMergeDiffSources() throws Exception {
        Interest<InstanceInfo> finalInterest = Interests.forFullRegistry();

        Source sourceA = InstanceModel.getDefaultModel().createSource(Source.Origin.LOCAL, "local");
        Source sourceB = InstanceModel.getDefaultModel().createSource(Source.Origin.REPLICATED, "rep1", 1);
        Source sourceC = InstanceModel.getDefaultModel().createSource(Source.Origin.REPLICATED, "rep1", 2);
        Source sourceD = InstanceModel.getDefaultModel().createSource(Source.Origin.REPLICATED, "rep2", 3);

        ChangeNotification<InstanceInfo> bufferStartA = SourcedStreamStateNotification.bufferStartNotification(finalInterest, sourceA);
        ChangeNotification<InstanceInfo> bufferEndA = SourcedStreamStateNotification.bufferEndNotification(finalInterest, sourceA);
        ChangeNotification<InstanceInfo> bufferStartB = SourcedStreamStateNotification.bufferStartNotification(finalInterest, sourceB);
        ChangeNotification<InstanceInfo> bufferEndB = SourcedStreamStateNotification.bufferEndNotification(finalInterest, sourceB);
        ChangeNotification<InstanceInfo> bufferStartC = SourcedStreamStateNotification.bufferStartNotification(finalInterest, sourceC);
        ChangeNotification<InstanceInfo> bufferEndC = SourcedStreamStateNotification.bufferEndNotification(finalInterest, sourceC);
        ChangeNotification<InstanceInfo> bufferStartD = SourcedStreamStateNotification.bufferStartNotification(finalInterest, sourceD);
        ChangeNotification<InstanceInfo> bufferEndD = SourcedStreamStateNotification.bufferEndNotification(finalInterest, sourceD);

        ChangeNotification<InstanceInfo> bufferStartNoSource = StreamStateNotification.bufferStartNotification(finalInterest);
        ChangeNotification<InstanceInfo> bufferEndNoSource = StreamStateNotification.bufferEndNotification(finalInterest);

        testStream
                .map(mergeFunctions.mergeDiffSources(finalInterest, new Func1<Source, String>() {
                    @Override
                    public String call(Source source) {
                        return source.getOriginNamePair();
                    }
                }))
                .filter(RxFunctions.filterNullValuesFunc())
                .observeOn(testScheduler)
                .subscribe(testSubscriber);

        onNext(bufferStartA);
        assertIsBufferStart(testSubscriber.takeNext());

        onNext(bufferStartA);
        assertThat(testSubscriber.takeNext(), is(nullValue()));

        onNext(bufferStartB);
        assertThat(testSubscriber.takeNext(), is(nullValue()));

        onNext(bufferEndB);
        assertThat(testSubscriber.takeNext(), is(nullValue()));

        onNext(bufferEndA);
        assertIsBufferEnd(testSubscriber.takeNext());

        onNext(bufferEndC);
        assertIsBufferEnd(testSubscriber.takeNext());

        onNext(bufferStartA);
        onNext(bufferStartB);
        onNext(bufferStartC);  // B and C are treated as the same per the match function
        onNext(bufferEndD);  // no op
        onNext(bufferEndB);  // waiting on A
        onNext(bufferStartD);
        onNext(bufferEndD);
        onNext(bufferEndA);
        List<ChangeNotification<InstanceInfo>> notifications = testSubscriber.takeNext(2);

        assertThat(notifications.size(), is(2));
        assertIsBufferStart(notifications.get(0));
        assertIsBufferEnd(notifications.get(1));

        onNext(bufferStartA);
        assertIsBufferStart(testSubscriber.takeNext());

        onNext(bufferStartNoSource);
        assertThat(testSubscriber.takeNext(), is(nullValue()));

        onNext(bufferEndA);
        assertThat(testSubscriber.takeNext(), is(nullValue()));

        onNext(bufferEndNoSource);
        assertIsBufferEnd(testSubscriber.takeNext());
    }

    @Test
    public void testMergeDiffAtomicInterests() throws Exception {
        Interest<InstanceInfo> interestA = Interests.forFullRegistry();
        Interest<InstanceInfo> interestB = Interests.forApplications("foo");
        Interest<InstanceInfo> interestC = Interests.forNone();
        Interest<InstanceInfo> interestD = Interests.forVips("bar");
        Interest<InstanceInfo> finalInterest = Interests.forSome(interestA, interestB, interestC, interestD);

        ChangeNotification<InstanceInfo> bufferStartA = StreamStateNotification.bufferStartNotification(interestA);
        ChangeNotification<InstanceInfo> bufferEndA = StreamStateNotification.bufferEndNotification(interestA);
        ChangeNotification<InstanceInfo> bufferStartB = StreamStateNotification.bufferStartNotification(interestB);
        ChangeNotification<InstanceInfo> bufferEndB = StreamStateNotification.bufferEndNotification(interestB);
        ChangeNotification<InstanceInfo> bufferStartC = StreamStateNotification.bufferStartNotification(interestC);
        ChangeNotification<InstanceInfo> bufferEndC = StreamStateNotification.bufferEndNotification(interestC);
        ChangeNotification<InstanceInfo> bufferStartD = StreamStateNotification.bufferStartNotification(interestD);
        ChangeNotification<InstanceInfo> bufferEndD = StreamStateNotification.bufferEndNotification(interestD);

        testStream
                .map(mergeFunctions.mergeDiffAtomicInterests(finalInterest))
                .filter(RxFunctions.filterNullValuesFunc())
                .observeOn(testScheduler)
                .subscribe(testSubscriber);

        onNext(bufferStartA);
        assertIsBufferStart(testSubscriber.takeNext(), finalInterest);

        onNext(bufferStartA);
        assertThat(testSubscriber.takeNext(), is(nullValue()));

        onNext(bufferStartB);
        assertThat(testSubscriber.takeNext(), is(nullValue()));

        onNext(bufferEndB);
        assertThat(testSubscriber.takeNext(), is(nullValue()));

        onNext(bufferEndA);
        assertIsBufferEnd(testSubscriber.takeNext(), finalInterest);

        onNext(bufferEndC);
        assertIsBufferEnd(testSubscriber.takeNext(), finalInterest);

        onNext(bufferStartA);
        onNext(bufferStartB);
        onNext(bufferStartC);
        onNext(bufferEndD);
        onNext(bufferEndB);
        onNext(bufferEndA);
        onNext(bufferStartD);
        onNext(bufferEndD);
        onNext(bufferEndC);
        List<ChangeNotification<InstanceInfo>> notifications = testSubscriber.takeNext(2);

        assertThat(notifications.size(), is(2));
        assertIsBufferStart(notifications.get(0), finalInterest);
        assertIsBufferEnd(notifications.get(1), finalInterest);

        Interest<InstanceInfo> multiple = Interests.forSome(Interests.forApplications("a"), Interests.forApplications("b"));
        onNext(bufferStartA);
        assertIsBufferStart(testSubscriber.takeNext(), finalInterest);
        onNext(StreamStateNotification.bufferStartNotification(multiple));
        assertThat(testSubscriber.takeNext(), is(nullValue()));  // drops the multiple
    }


    private void onNext(ChangeNotification<InstanceInfo> notification) {
        testStream.onNext(notification);
        testScheduler.triggerActions();
    }

    private static void assertIsBufferStart(ChangeNotification<InstanceInfo> notification) {
        assertThat(notification.isStreamStateNotification(), is(true));
        StreamStateNotification<InstanceInfo> n = (StreamStateNotification) notification;
        assertThat(notification, not(instanceOf(Sourced.class)));
        assertThat(n.getBufferState(), is(StreamStateNotification.BufferState.BufferStart));
    }

    private static void assertIsBufferEnd(ChangeNotification<InstanceInfo> notification) {
        assertThat(notification.isStreamStateNotification(), is(true));
        StreamStateNotification<InstanceInfo> n = (StreamStateNotification) notification;
        assertThat(notification, not(instanceOf(Sourced.class)));
        assertThat(n.getBufferState(), is(StreamStateNotification.BufferState.BufferEnd));
    }

    private static void assertIsBufferStart(ChangeNotification<InstanceInfo> notification, Interest<InstanceInfo> finalInterest) {
        assertIsBufferStart(notification);
        StreamStateNotification<InstanceInfo> n = (StreamStateNotification) notification;
        assertThat(n.getInterest(), is(finalInterest));
    }

    private static void assertIsBufferEnd(ChangeNotification<InstanceInfo> notification, Interest<InstanceInfo> finalInterest) {
        assertIsBufferEnd(notification);
        StreamStateNotification<InstanceInfo> n = (StreamStateNotification) notification;
        assertThat(n.getInterest(), is(finalInterest));
    }
}
