/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.utils.rx;

import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Test;
import rx.Observable;
import rx.subjects.PublishSubject;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static com.netflix.eureka2.utils.rx.PeekOperator.peek;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 */
public class PeekOperatorTest {

    private final PublishSubject<String> sourceSubject = PublishSubject.create();
    private final AtomicReference<String> headRef = new AtomicReference<>();
    private final ExtTestSubscriber<String> testSubscriber = new ExtTestSubscriber<>();

    @Test
    public void testPeekAhead() throws Exception {
        setupForwardingHandler();

        sourceSubject.onNext("A");
        sourceSubject.onNext("B");
        assertThat(headRef.get(), is(equalTo("A")));
        assertThat(testSubscriber.takeNext(), is(equalTo("A")));
        assertThat(testSubscriber.takeNext(), is(equalTo("B")));
    }

    @Test
    public void testErrorPropagationOnFirst() throws Exception {
        setupForwardingHandler();

        sourceSubject.onError(new IOException("Simulated error"));
        testSubscriber.assertOnError();
    }

    @Test
    public void testErrorPropagationOnNext() throws Exception {
        setupForwardingHandler();

        sourceSubject.onNext("A");
        sourceSubject.onError(new IOException("Simulated error"));
        testSubscriber.assertOnError();
    }

    @Test
    public void testOnCompletedPropagationOnFirst() throws Exception {
        setupForwardingHandler();

        sourceSubject.onCompleted();
        testSubscriber.assertOnCompleted();
    }

    @Test
    public void testOnCompletedPropagationOnNext() throws Exception {
        setupForwardingHandler();

        sourceSubject.onNext("A");
        assertThat(testSubscriber.takeNext(), is(equalTo("A")));

        sourceSubject.onCompleted();
        testSubscriber.assertOnCompleted();
    }

    @Test
    public void testErrorInHandlerPropagation() throws Exception {
        sourceSubject.compose(peek((head, observable) -> {
            headRef.set(head);
            return observable.take(1).concatWith(Observable.error(new IOException("Simulated error")));
        })).subscribe(testSubscriber);

        sourceSubject.onNext("A");
        assertThat(testSubscriber.takeNext(), is(equalTo("A")));
        testSubscriber.assertOnError();
    }

    private void setupForwardingHandler() throws Exception {
        sourceSubject.compose(peek((head, observable) -> {
            headRef.set(head);
            return observable;
        })).subscribe(testSubscriber);
    }
}