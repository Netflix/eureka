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

package com.netflix.eureka2.client.registry.swap;

import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.eureka2.client.registry.EurekaClientRegistry;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Test;
import rx.functions.Action0;
import rx.subjects.PublishSubject;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class RegistrySwapOperatorTest {

    public static final int MIN_PERCENTAGE = 80;
    public static final int RELAX_INTERVAL_MS = 1000;

    private static final ChangeNotification<InstanceInfo> CHANGE_NOTIFICATION =
            new ChangeNotification<>(Kind.Add, SampleInstanceInfo.DiscoveryServer.build());

    private final EurekaClientRegistry<InstanceInfo> previousRegistry = mock(EurekaClientRegistry.class);
    private final EurekaClientRegistry<InstanceInfo> currentRegistry = mock(EurekaClientRegistry.class);
    private final RegistrySwapStrategyFactory strategyFactory = ThresholdStrategy.factoryFor(MIN_PERCENTAGE, RELAX_INTERVAL_MS);

    private final AtomicBoolean done = new AtomicBoolean();

    @Test
    public void testTerminatesStreamWhenThresholdReachedImmediately() throws Exception {
        connect();
        assertThat(done.get(), is(equalTo(true)));
    }

    @Test
    public void testTerminatesStreamWhenThresholdReached() throws Exception {
        when(previousRegistry.size()).thenReturn(2);
        PublishSubject<ChangeNotification<InstanceInfo>> stream = connect();

        assertThat(done.get(), is(equalTo(false)));

        when(currentRegistry.size()).thenReturn(1);
        stream.onNext(CHANGE_NOTIFICATION);
        assertThat(done.get(), is(equalTo(false)));

        when(currentRegistry.size()).thenReturn(2);
        stream.onNext(CHANGE_NOTIFICATION);
        assertThat(done.get(), is(equalTo(true)));
    }

    protected PublishSubject<ChangeNotification<InstanceInfo>> connect() {
        PublishSubject<ChangeNotification<InstanceInfo>> stream = PublishSubject.create();
        stream.lift(new RegistrySwapOperator(previousRegistry, currentRegistry, strategyFactory)).doOnCompleted(new Action0() {
            @Override
            public void call() {
                done.set(true);
            }
        }).subscribe();

        return stream;
    }
}