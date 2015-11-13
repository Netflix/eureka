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

package com.netflix.eureka2.testkit.internal.rx;

import rx.Observable;
import rx.functions.Action2;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.functions.Func2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Tomasz Bak
 */
public class Observables {

    /**
     * An observable that runs all observables in parallel, but returns ordered sequence of elements.
     * It is not optimized implementation, first collecting all elements, then restoring the order.
     */
    public static <T> Observable<T> parallelConcat(Observable<T>... observables) {
        List<Observable<ItemCollector<T>>> buckets = new ArrayList<Observable<ItemCollector<T>>>();

        for (int i = 0; i < observables.length; i++) {
            final int j = i;
            buckets.add(observables[j].collect(
                    new Func0<ItemCollector<T>>() {
                        @Override
                        public ItemCollector<T> call() {
                            return new ItemCollector<>(j);
                        }
                    },
                    new Action2<ItemCollector<T>, T>() {
                @Override
                public void call(ItemCollector<T> collector, T t) {
                    collector.add(t);
                }
            }));
        }

        return Observable.merge(buckets).reduce(new ArrayList<ItemCollector<T>>(), new Func2<ArrayList<ItemCollector<T>>, ItemCollector<T>, ArrayList<ItemCollector<T>>>() {
            @Override
            public ArrayList<ItemCollector<T>> call(ArrayList<ItemCollector<T>> acc, ItemCollector<T> item) {
                acc.add(item);
                return acc;
            }
        }).flatMap(new Func1<ArrayList<ItemCollector<T>>, Observable<T>>() {
            @Override
            public Observable<T> call(ArrayList<ItemCollector<T>> itemCollectors) {
                Collections.sort(itemCollectors, new Comparator<ItemCollector<T>>() {
                    @Override
                    public int compare(ItemCollector<T> o1, ItemCollector<T> o2) {
                        return o1.index < o2.index ? -1 : o1.index > o2.index ? 1 : 0;
                    }
                });

                List<Observable<T>> itemObservableList = new ArrayList<Observable<T>>(itemCollectors.size());
                for (ItemCollector<T> ic : itemCollectors) {
                    itemObservableList.add(Observable.from(ic.items));
                }
                return Observable.concat(Observable.from(itemObservableList));
            }
        });
    }

    static class ItemCollector<T> {
        private final int index;
        private final ConcurrentLinkedQueue<T> items = new ConcurrentLinkedQueue<T>();

        ItemCollector(int index) {
            this.index = index;
        }

        public void add(T t) {
            items.add(t);
        }
    }
}
