package com.netflix.eureka2.interests;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import org.junit.Test;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.utils.ExtCollections.asSet;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class ChangeNotificationsTest {

    private static final TestScheduler testScheduler = Schedulers.test();

    private static final Interest<String> INTEREST = new FullRegistryInterest<>();

    private static final ChangeNotification<String> ADD_A = new ChangeNotification<>(Kind.Add, "A");
    private static final ChangeNotification<String> DELETE_A = new ChangeNotification<>(Kind.Delete, "A");
    private static final ChangeNotification<String> MODIFY_A = new ChangeNotification<>(Kind.Modify, "A");
    private static final ChangeNotification<String> ADD_B = new ChangeNotification<>(Kind.Add, "B");
    private static final ChangeNotification<String> ADD_C = new ChangeNotification<>(Kind.Add, "C");
    private static final ChangeNotification<String> MODIFY_D = new ChangeNotification<>(Kind.Modify, "D");
    private static final ChangeNotification<String> DELETE_E = new ChangeNotification<>(Kind.Delete, "E");

    private static final List<ChangeNotification<String>> FIRST_BATCH = asList(ADD_A, ADD_B);
    private static final List<ChangeNotification<String>> SECOND_BATCH = asList(DELETE_A, ADD_C);

    private static final List<ChangeNotification<String>> NOTIFICATION_LIST_1 = asList(
            ADD_A, DELETE_A, ADD_A, ADD_B, MODIFY_A, MODIFY_D, DELETE_E
    );
    private static final List<ChangeNotification<String>> NOTIFICATION_LIST_2 = asList(DELETE_A, ADD_C);

    private static final List<ChangeNotification<String>> COLLAPSED_NOTIFICATION_LIST_1 = asList(ADD_B, ADD_A, MODIFY_D, DELETE_E);
    private static final List<ChangeNotification<String>> COLLAPSED_NOTIFICATION_LIST_2 = NOTIFICATION_LIST_2;
    private static final List<ChangeNotification<String>> COLLAPSED_NOTIFICATION_LISTS_1_AND_2 = asList(ADD_B, MODIFY_D, DELETE_E, DELETE_A, ADD_C);

    private static final Comparator<String> STRING_COMPARATOR = new Comparator<String>() {
        @Override
        public int compare(String o1, String o2) {
            return o1.compareTo(o2);
        }
    };

    @Test
    public void testChangeNotificationListEvaluation() throws Exception {
        SortedSet<String> result = ChangeNotifications.evaluate(NOTIFICATION_LIST_1, STRING_COMPARATOR);
        assertThat(result, hasItems("A", "B"));
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
    public void testSnapshotFunctionGeneratesDistinctValueSets() throws Exception {
        PublishSubject<List<ChangeNotification<String>>> notificationSubject = PublishSubject.create();

        ExtTestSubscriber<Set<String>> testSubscriber = new ExtTestSubscriber<>();
        notificationSubject.compose(ChangeNotifications.<String>snapshots()).subscribe(testSubscriber);

        notificationSubject.onNext(FIRST_BATCH);
        assertThat(testSubscriber.takeNext(), is(equalTo((Set) asSet("A", "B"))));
        notificationSubject.onNext(SECOND_BATCH);
        assertThat(testSubscriber.takeNext(), is(equalTo((Set) asSet("B", "C"))));

        // Verify that the same snapshot is issued if no data are changed on a prompt
        notificationSubject.onNext(SECOND_BATCH);
        assertThat(testSubscriber.takeNext(), is(equalTo((Set) asSet("B", "C"))));

        // Verify that the same snapshot is issued if an empty prompt arrives
        notificationSubject.onNext(Collections.EMPTY_LIST);
        assertThat(testSubscriber.takeNext(), is(equalTo((Set) asSet("B", "C"))));
    }

    @Test
    public void testCollapsingOfChangeNotificationList() throws Exception {
        List<ChangeNotification<String>> collapsed = Observable.just(NOTIFICATION_LIST_1)
                .compose(ChangeNotifications.collapse(String.CASE_INSENSITIVE_ORDER))
                .toBlocking().first();

        assertThat(collapsed, is(equalTo(COLLAPSED_NOTIFICATION_LIST_1)));
    }

    @Test
    public void testCollapsingOfChangeNotificationListOfList() throws Exception {
        List<ChangeNotification<String>> collapsed = Observable.just(
                asList(NOTIFICATION_LIST_1, NOTIFICATION_LIST_2)
        ).compose(ChangeNotifications.collapseLists(String.CASE_INSENSITIVE_ORDER)).toBlocking().first();

        assertThat(collapsed, is(equalTo(COLLAPSED_NOTIFICATION_LISTS_1_AND_2)));
    }

    @Test
    public void testAggregateChangesEmitsItemsAfterEachInterval() throws Exception {
        PublishSubject<List<ChangeNotification<String>>> source = PublishSubject.create();

        ExtTestSubscriber<List<ChangeNotification<String>>> testSubscriber = new ExtTestSubscriber<>();
        source.compose(
                ChangeNotifications.aggregateChanges(STRING_COMPARATOR, 1, TimeUnit.SECONDS, testScheduler)
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
        PublishSubject<List<ChangeNotification<String>>> source = PublishSubject.create();

        ExtTestSubscriber<List<ChangeNotification<String>>> testSubscriber = new ExtTestSubscriber<>();
        source.compose(
                ChangeNotifications.emitAndAggregateChanges(STRING_COMPARATOR, 1, TimeUnit.SECONDS, testScheduler)
        ).subscribe(testSubscriber);

        // Emit first item before interval passes
        source.onNext(NOTIFICATION_LIST_1);
        assertThat(testSubscriber.takeNext(), is(COLLAPSED_NOTIFICATION_LIST_1));

        // Emit another value and pass the interval
        source.onNext(NOTIFICATION_LIST_2);
        testScheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        assertThat(testSubscriber.takeNext(), is(equalTo(COLLAPSED_NOTIFICATION_LIST_2)));
    }
}