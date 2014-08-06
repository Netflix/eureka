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

import java.io.PrintStream;

import rx.Observable;
import rx.Observer;

/**
 * @author Tomasz Bak
 */
public class RxSniffer implements Observer<Object> {

    private final String name;
    private final PrintStream out;

    public RxSniffer(String name) {
        this.name = name;
        out = System.out;
    }

    @Override
    public void onCompleted() {
        out.println(name + ".onCompleted");
    }

    @Override
    public void onError(Throwable e) {
        out.println(name + ".onError");
        e.printStackTrace(out);
    }

    @Override
    public void onNext(Object o) {
        out.println(name + ".onNext(" + o + ')');
    }

    public static <T> Observable<T> sniff(String name, Observable<T> observable) {
        observable.subscribe(new RxSniffer(name));
        return observable;
    }
}
