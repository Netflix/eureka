package com.netflix.eureka2.utils.functions;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.utils.functions.ChangeNotifications.Identity;
import com.netflix.eureka2.interests.FullRegistryInterest;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import org.junit.Test;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.utils.ExtCollections.asSet;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class ChangeNotificationsTest {

    private static final TestScheduler testScheduler = Schedulers.test();

    private static final Interest<String> INTEREST = new FullRegistryInterest<>();

    private static final Identity<String, String> CUSTOM_TEST_IDENTITY = new Identity<String, String>() {
        @Override
        public String getId(String data) {
            return data.substring(0, 1);  // use the first char of the test data as the identity
        }
    };

    private static final ChangeNotification<String> ADD_A = new ChangeNotification<>(Kind.Add, "AA");
    private static final ChangeNotification<String> DELETE_A = new ChangeNotification<>(Kind.Delete, "AA");
    private static final ChangeNotification<String> MODIFY_A = new ChangeNotification<>(Kind.Modify, "Aa");
    private static final ChangeNotification<String> ADD_B = new ChangeNotification<>(Kind.Add, "BB");
    private static final ChangeNotification<String> MODIFY_B = new ChangeNotification<>(Kind.Add, "Bb");
    private static final ChangeNotification<String> ADD_C = new ChangeNotification<>(Kind.Add, "CC");
    private static final ChangeNotification<String> ADD_D = new ChangeNotification<>(Kind.Add, "DD");
    private static final ChangeNotification<String> MODIFY_D = new ChangeNotification<>(Kind.Modify, "Dd");
    private static final ChangeNotification<String> DELETE_E = new ChangeNotification<>(Kind.Delete, "EE");

    private static final List<ChangeNotification<String>> FIRST_BATCH = asList(ADD_A, ADD_B);
    private static final List<ChangeNotification<String>> SECOND_BATCH = asList(DELETE_A, ADD_C);

    private static final List<ChangeNotification<String>> NOTIFICATION_LIST_1 = asList(
            ADD_A, DELETE_A, ADD_A, ADD_B, MODIFY_A, MODIFY_D, DELETE_E
    );
    private static final List<ChangeNotification<String>> NOTIFICATION_LIST_2 = asList(DELETE_A, MODIFY_B, ADD_C);

    private static final List<ChangeNotification<String>> COLLAPSED_NOTIFICATION_LIST_1 = asList(ADD_B, MODIFY_A, MODIFY_D, DELETE_E);
    private static final List<ChangeNotification<String>> COLLAPSED_NOTIFICATION_LIST_2 = NOTIFICATION_LIST_2;
    private static final List<ChangeNotification<String>> COLLAPSED_NOTIFICATION_LISTS_1_AND_2 = asList(MODIFY_D, DELETE_E, DELETE_A, MODIFY_B, ADD_C);

    @Test
    public void testChangeNotificationListEvaluation() throws Exception {
        LinkedHashSet<String> result = ChangeNotifications.collapseAndExtract(NOTIFICATION_LIST_1, CUSTOM_TEST_IDENTITY);
        assertThat(result, contains("BB", "Aa", "Dd"));
    }

    @Test
    public void testDelineatedBuffersFunctionGeneratesBufferList() throws Exception {
        PublishSubject<ChangeNotification<String>> notificationSubject = PublishSubject.create();

        ExtTestSubscriber<List<ChangeNotification<String>>> testSubscriber = new ExtTestSubscriber<>();
        notificationSubject.compose(ChangeNotifications.<String>delineatedBuffers()).subscribe(testSubscriber);

        // Emit batch of two
        notificationSubject.onNext(StreamStateNotification.bufferStartNotification(INTEREST));
        notificationSubject.onNext(ADD_A);
        notificationSubject.onNext(ADD_B);
        notificationSubject.onNext(StreamStateNotification.bufferEndNotification(INTEREST));
        assertThat(testSubscriber.takeNext().size(), is(equalTo(2)));

        // Emit batch of 1
        notificationSubject.onNext(ADD_C);
        assertThat(testSubscriber.takeNext().size(), is(equalTo(1)));

        // Ensure empty batches are not emitted
        notificationSubject.onNext(StreamStateNotification.bufferStartNotification(INTEREST));
        notificationSubject.onNext(StreamStateNotification.bufferEndNotification(INTEREST));
        assertThat(testSubscriber.takeNext(), is(nullValue()));
    }

    @Test
    public void testDelineatedBuffersThenSnapshotsHandleModify() throws Exception {
        PublishSubject<ChangeNotification<String>> notificationSubject = PublishSubject.create();

        ExtTestSubscriber<Set<String>> testSubscriber = new ExtTestSubscriber<>();
        notificationSubject
                .compose(ChangeNotifications.<String>delineatedBuffers())
                .compose(ChangeNotifications.snapshots(CUSTOM_TEST_IDENTITY))
                .subscribe(testSubscriber);

        // Emit initial add
        notificationSubject.onNext(StreamStateNotification.bufferStartNotification(INTEREST));
        notificationSubject.onNext(ADD_A);
        notificationSubject.onNext(StreamStateNotification.bufferEndNotification(INTEREST));
        assertThat(testSubscriber.takeNext().size(), is(equalTo(1)));

        // Emit modify
        notificationSubject.onNext(MODIFY_A);
        assertThat(testSubscriber.takeNext().size(), is(equalTo(1)));

        // Ensure empty batches are not emitted
        notificationSubject.onNext(StreamStateNotification.bufferStartNotification(INTEREST));
        notificationSubject.onNext(StreamStateNotification.bufferEndNotification(INTEREST));
        assertThat(testSubscriber.takeNext(), is(nullValue()));
    }

    @Test
    public void testSnapshotFunctionGeneratesDistinctValueSets() throws Exception {
        PublishSubject<List<ChangeNotification<String>>> notificationSubject = PublishSubject.create();

        ExtTestSubscriber<Set<String>> testSubscriber = new ExtTestSubscriber<>();
        notificationSubject.compose(ChangeNotifications.snapshots(CUSTOM_TEST_IDENTITY)).subscribe(testSubscriber);

        notificationSubject.onNext(FIRST_BATCH);
        assertThat(testSubscriber.takeNext(), is(equalTo((Set) asSet("AA", "BB"))));
        notificationSubject.onNext(SECOND_BATCH);
        assertThat(testSubscriber.takeNext(), is(equalTo((Set) asSet("BB", "CC"))));

        // Verify that the same snapshot is issued if no data are changed on a prompt
        notificationSubject.onNext(SECOND_BATCH);
        assertThat(testSubscriber.takeNext(), is(equalTo((Set) asSet("BB", "CC"))));

        // Verify that the same snapshot is issued if an empty prompt arrives
        notificationSubject.onNext(Collections.EMPTY_LIST);
        assertThat(testSubscriber.takeNext(), is(equalTo((Set) asSet("BB", "CC"))));
    }

    @Test
    public void testCollapsingOfChangeNotificationList() throws Exception {
        List<ChangeNotification<String>> collapsed = Observable.just(NOTIFICATION_LIST_1)
                .compose(ChangeNotifications.collapse(CUSTOM_TEST_IDENTITY))
                .toBlocking().first();

        assertThat(collapsed, is(equalTo(COLLAPSED_NOTIFICATION_LIST_1)));
    }

    @Test
    public void testCollapsingOfChangeNotificationListOfList() throws Exception {
        List<ChangeNotification<String>> collapsed = Observable.just(
                asList(NOTIFICATION_LIST_1, NOTIFICATION_LIST_2)
        ).compose(ChangeNotifications.collapseLists(CUSTOM_TEST_IDENTITY)).toBlocking().first();

        assertThat(collapsed, is(equalTo(COLLAPSED_NOTIFICATION_LISTS_1_AND_2)));
    }

    @Test
    public void testAggregateChangesEmitsItemsAfterEachInterval() throws Exception {
        PublishSubject<List<ChangeNotification<String>>> source = PublishSubject.create();

        ExtTestSubscriber<List<ChangeNotification<String>>> testSubscriber = new ExtTestSubscriber<>();
        source.compose(
                ChangeNotifications.aggregateChanges(CUSTOM_TEST_IDENTITY, 1, TimeUnit.SECONDS, testScheduler)
        ).subscribe(testSubscriber);

        // Emit first item before interval passes
        source.onNext(NOTIFICATION_LIST_1);
        assertThat(testSubscriber.takeNext(), is(nullValue()));

        // Emit another value and pass the interval
        source.onNext(NOTIFICATION_LIST_2);
        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        assertThat(testSubscriber.takeNext(), is(equalTo(COLLAPSED_NOTIFICATION_LISTS_1_AND_2)));
    }

    @Test
    public void testEmitAndAggregateChangesEmitsFirstItemImmediately() throws Exception {
        ReplaySubject<List<ChangeNotification<String>>> source = ReplaySubject.create();
        // Emit first item, so it is available at time 0
        source.onNext(NOTIFICATION_LIST_1);

        ExtTestSubscriber<List<ChangeNotification<String>>> testSubscriber = new ExtTestSubscriber<>();
        source.compose(
                ChangeNotifications.emitAndAggregateChanges(CUSTOM_TEST_IDENTITY, 1, TimeUnit.SECONDS, testScheduler)
        ).subscribe(testSubscriber);

        testScheduler.triggerActions();
        assertThat(testSubscriber.takeNext(), is(COLLAPSED_NOTIFICATION_LIST_1));

        // Emit another value and pass the interval
        source.onNext(NOTIFICATION_LIST_2);
        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        assertThat(testSubscriber.takeNext(), is(equalTo(COLLAPSED_NOTIFICATION_LIST_2)));
    }

    @Test
    public void testBuffersFunctionGeneratesBufferList() throws Exception {
        PublishSubject<ChangeNotification<String>> notificationSubject = PublishSubject.create();

        ExtTestSubscriber<List<ChangeNotification<String>>> testSubscriber = new ExtTestSubscriber<>();
        notificationSubject.compose(ChangeNotifications.<String>buffers(true)).subscribe(testSubscriber);

        // Emit batch of two
        notificationSubject.onNext(ADD_A);
        notificationSubject.onNext(ADD_B);
        notificationSubject.onNext(ChangeNotification.<String>bufferSentinel());
        assertThat(testSubscriber.takeNext().size(), is(equalTo(2)));

        // Emit batch of 1
        notificationSubject.onNext(ADD_C);
        notificationSubject.onNext(ChangeNotification.<String>bufferSentinel());
        assertThat(testSubscriber.takeNext().size(), is(equalTo(1)));

        // Ensure empty batches are emitted as empty lists
        notificationSubject.onNext(ChangeNotification.<String>bufferSentinel());
        assertThat(testSubscriber.takeNext().size(), is(equalTo(0)));

        // assert onComplete of the input stream triggers a buffer emit
        notificationSubject.onNext(ADD_D);
        notificationSubject.onCompleted();
        assertThat(testSubscriber.takeNext().size(), is(equalTo(1)));
        testSubscriber.assertOnCompleted();
    }

    @Test
    public void testBuffersFunctionPropagateErrorEmptyBatch() throws Exception {
        PublishSubject<ChangeNotification<String>> notificationSubject = PublishSubject.create();

        ExtTestSubscriber<List<ChangeNotification<String>>> testSubscriber = new ExtTestSubscriber<>();
        notificationSubject.compose(ChangeNotifications.<String>buffers()).subscribe(testSubscriber);

        // Emit batch of two
        notificationSubject.onNext(ADD_A);
        notificationSubject.onNext(ADD_B);
        notificationSubject.onNext(ChangeNotification.<String>bufferSentinel());
        assertThat(testSubscriber.takeNext().size(), is(equalTo(2)));

        // assert onComplete of the input stream triggers a buffer emit
        Exception expected = new Exception("testException");
        notificationSubject.onError(expected);
        testSubscriber.assertOnError(expected);
        assertThat(testSubscriber.takeNext(), is(nullValue()));
    }

    @Test
    public void testBuffersFunctionPropagateErrorPartialBatch() throws Exception {
        PublishSubject<ChangeNotification<String>> notificationSubject = PublishSubject.create();

        ExtTestSubscriber<List<ChangeNotification<String>>> testSubscriber = new ExtTestSubscriber<>();
        notificationSubject.compose(ChangeNotifications.<String>buffers()).subscribe(testSubscriber);

        // Emit batch of two
        notificationSubject.onNext(ADD_A);
        notificationSubject.onNext(ADD_B);
        notificationSubject.onNext(ChangeNotification.<String>bufferSentinel());
        assertThat(testSubscriber.takeNext().size(), is(equalTo(2)));

        // assert onComplete of the input stream triggers a buffer emit
        notificationSubject.onNext(ADD_D);
        Exception expected = new Exception("testException");
        notificationSubject.onError(expected);
        testSubscriber.assertOnError(expected);
        assertThat(testSubscriber.takeNext(), is(nullValue()));
    }
}