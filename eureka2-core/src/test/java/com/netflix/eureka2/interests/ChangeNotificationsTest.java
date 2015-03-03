package com.netflix.eureka2.interests;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import org.junit.Test;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.utils.ExtCollections.asSet;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class ChangeNotificationsTest {

    private static final Interest<String> INTEREST = new FullRegistryInterest<>();

    private static final ChangeNotification<String> ADD_A = new ChangeNotification<>(Kind.Add, "A");
    private static final ChangeNotification<String> DELETE_A = new ChangeNotification<>(Kind.Delete, "A");
    private static final ChangeNotification<String> ADD_B = new ChangeNotification<>(Kind.Add, "B");
    private static final ChangeNotification<String> ADD_C = new ChangeNotification<>(Kind.Add, "C");

    private static final List<ChangeNotification<String>> FIRST_BATCH = Arrays.asList(ADD_A, ADD_B);
    private static final List<ChangeNotification<String>> SECOND_BATCH = Arrays.asList(DELETE_A, ADD_C);

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

        // Verify that no snapshot is issued if no data are changed
        notificationSubject.onNext(SECOND_BATCH);
        assertThat(testSubscriber.takeNext(), is(nullValue()));
    }
}