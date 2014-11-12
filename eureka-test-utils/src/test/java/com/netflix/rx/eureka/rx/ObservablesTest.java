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

package com.netflix.rx.eureka.rx;

import org.junit.Test;
import rx.Observable;
import rx.subjects.PublishSubject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Tomasz Bak
 */
public class ObservablesTest {

    @Test
    public void testParallelConcat() throws Exception {
        PublishSubject<Integer> first = PublishSubject.create();
        PublishSubject<Integer> second = PublishSubject.create();

        Observable<Integer> parallelConcat = Observables.parallelConcat(first, second);
        Iterator<Integer> it = parallelConcat.toBlocking().getIterator();

        first.onNext(5);
        second.onNext(2);
        first.onNext(3);
        second.onNext(11);
        second.onCompleted();
        first.onCompleted();

        List<Integer> result = new ArrayList<Integer>();
        while (it.hasNext()) {
            result.add(it.next());
        }

        assertEquals(Arrays.asList(5, 3, 2, 11), result);
    }
}