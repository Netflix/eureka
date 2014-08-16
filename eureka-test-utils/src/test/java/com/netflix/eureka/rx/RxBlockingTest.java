/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka.rx;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.netflix.eureka.rx.RxBlocking.RxItem;
import org.junit.Test;
import rx.Observable;
import rx.subjects.PublishSubject;

import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class RxBlockingTest {

    @Test
    public void testFirstFrom() throws Exception {
        PublishSubject<Integer> subject = PublishSubject.create();

        RxItem<Integer> rxItem = RxBlocking.firstFrom(1, TimeUnit.SECONDS, subject);
        subject.onNext(1);

        assertEquals("Invalid value", new Integer(1), rxItem.item());
    }

    @Test(expected = TimeoutException.class)
    public void testFirstFromTimeout() throws Exception {
        RxBlocking.firstFrom(1, TimeUnit.MICROSECONDS, PublishSubject.<Integer>create()).item();
    }

    @Test
    public void testFirstFromEach() throws Exception {
        PublishSubject<Integer> subject1 = PublishSubject.create();
        PublishSubject<Integer> subject2 = PublishSubject.create();

        RxItem<List<Integer>> rxItems = RxBlocking.firstFromEach(1, TimeUnit.SECONDS, subject1, subject2);

        subject1.onNext(1);
        subject2.onNext(2);

        assertEquals("Invalid sequence of elements", Arrays.asList(1, 2), rxItems.item());
    }

    @Test(expected = TimeoutException.class)
    public void testFirstFromEachTimeout() throws Exception {
        RxBlocking.firstFromEach(1, TimeUnit.MICROSECONDS,
                PublishSubject.<Integer>create(), PublishSubject.<Integer>create()).item();
    }

    @Test
    public void testIterator() throws Exception {
        PublishSubject<Integer> subject = PublishSubject.create();
        Iterator<Integer> iterator = RxBlocking.iteratorFrom(1, TimeUnit.SECONDS, subject);

        subject.onNext(1);
        assertEquals("Unexpected value", new Integer(1), iterator.next());

        subject.onCompleted();
        assertFalse("Iterator should be closed", iterator.hasNext());
    }

    @Test
    public void testVoidObservable() throws Exception {
        Iterator<Object> iterator = RxBlocking.iteratorFrom(1, TimeUnit.SECONDS, Observable.empty());
        assertFalse("Iterator should be closed", iterator.hasNext());
    }

    @Test
    public void testIteratorHasNextTimeout() throws Exception {
        PublishSubject<Integer> subject = PublishSubject.create();
        Iterator<Integer> iterator = RxBlocking.iteratorFrom(1, TimeUnit.MILLISECONDS, subject);

        try {
            iterator.hasNext();
            fail("Expected timeout exception");
        } catch (RuntimeException e) {
            assertTrue("Expected timeout exception", e.getCause() instanceof TimeoutException);
        }
    }

    @Test
    public void testIteratorNextTimeout() throws Exception {
        PublishSubject<Integer> subject = PublishSubject.create();
        Iterator<Integer> iterator = RxBlocking.iteratorFrom(1, TimeUnit.MILLISECONDS, subject);

        try {
            iterator.next();
            fail("Expected timeout exception");
        } catch (RuntimeException e) {
            assertTrue("Expected timeout exception", e.getCause() instanceof TimeoutException);
        }
    }
}