package com.netflix.eureka2.utils.functions;

import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.utils.functions.RxFunctions;
import org.junit.Test;
import rx.Observable;
import rx.functions.Func2;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class RxFunctionsTest {

    @Test
    public void testCombineWithOptionalCompletesWhenMainObservableTerminates() throws Exception {
        PublishSubject<String> mainStream = PublishSubject.create();
        PublishSubject<Integer> optionalStream = PublishSubject.create();

        ExtTestSubscriber<String> testSubscriber = new ExtTestSubscriber<>();
        RxFunctions.combineWithOptional(mainStream, optionalStream, new Func2<String, Integer, String>() {
            @Override
            public String call(String text, Integer counter) {
                return text + '#' + counter;
            }
        }).subscribe(testSubscriber);

        mainStream.onNext("A");
        optionalStream.onNext(1);

        assertThat(testSubscriber.takeNext(), is(equalTo("A#1")));
        mainStream.onCompleted();

        assertThat(testSubscriber.takeNext(), is(nullValue()));
        testSubscriber.assertOnCompleted();
    }

    @Test
    public void testCombineWithOptionalDoesNotCompleteWhenOptionalObservableTerminates() throws Exception {
        PublishSubject<String> mainStream = PublishSubject.create();
        PublishSubject<Integer> optionalStream = PublishSubject.create();

        ExtTestSubscriber<String> testSubscriber = new ExtTestSubscriber<>();
        RxFunctions.combineWithOptional(mainStream, optionalStream, new Func2<String, Integer, String>() {
            @Override
            public String call(String text, Integer counter) {
                return text + '#' + counter;
            }
        }).subscribe(testSubscriber);

        mainStream.onNext("A");
        optionalStream.onNext(1);

        assertThat(testSubscriber.takeNext(), is(equalTo("A#1")));

        optionalStream.onCompleted();
        testSubscriber.assertOpen();

        mainStream.onNext("B");
        assertThat(testSubscriber.takeNext(), is(equalTo("B#1")));
    }

    @Test
    public void testSwallowErrorFunc() {
        TestSubscriber<Void> testSubscriber = new TestSubscriber<>();

        Observable.<Void>error(new RuntimeException("test exception"))
                .compose(RxFunctions.<Void>swallowError())
                .subscribe(testSubscriber);

        testSubscriber.assertNoErrors();
        testSubscriber.assertTerminalEvent();
    }
}