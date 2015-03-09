package com.netflix.eureka2.client.functions;

import java.util.List;
import java.util.Set;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import org.junit.Test;
import rx.subjects.PublishSubject;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class ChangeNotificationFunctionsTest {

    private static final ChangeNotification<String> ADD_A = new ChangeNotification<>(Kind.Add, "A");
    private static final ChangeNotification<String> ADD_B = new ChangeNotification<>(Kind.Add, "B");
    private static final ChangeNotification<String> ADD_C = new ChangeNotification<>(Kind.Add, "C");
    private static final ChangeNotification<String> ADD_D = new ChangeNotification<>(Kind.Add, "D");

    @Test
    public void testBuffersFunctionGeneratesBufferList() throws Exception {
        PublishSubject<ChangeNotification<String>> notificationSubject = PublishSubject.create();

        ExtTestSubscriber<List<ChangeNotification<String>>> testSubscriber = new ExtTestSubscriber<>();
        notificationSubject.compose(ChangeNotificationFunctions.<String>buffers()).subscribe(testSubscriber);

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
        notificationSubject.compose(ChangeNotificationFunctions.<String>buffers()).subscribe(testSubscriber);

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
        notificationSubject.compose(ChangeNotificationFunctions.<String>buffers()).subscribe(testSubscriber);

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