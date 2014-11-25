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

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.registry.EurekaClientRegistry;
import com.netflix.eureka2.registry.InstanceInfo;
import org.junit.Test;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Tomasz Bak
 */
public class ThresholdStrategyTest {

    public static final int MIN_PERCENTAGE = 80;
    public static final int RELAX_INTERVAL_MS = 1000;

    private final EurekaClientRegistry<InstanceInfo> previousRegistry = mock(EurekaClientRegistry.class);
    private final EurekaClientRegistry<InstanceInfo> currentRegistry = mock(EurekaClientRegistry.class);

    private final TestScheduler scheduler = Schedulers.test();
    private final RegistrySwapStrategy strategy = ThresholdStrategy.factoryFor(MIN_PERCENTAGE, RELAX_INTERVAL_MS, scheduler).newInstance();

    @Test
    public void testDetectsWhenAboveThreshold() throws Exception {
        // Empty registry
        when(previousRegistry.size()).thenReturn(0);
        assertReady();

        // Something in the registry
        when(previousRegistry.size()).thenReturn(10);
        when(currentRegistry.size()).thenReturn(9);

        assertReady();
    }

    @Test
    public void testDetectsWhenBelowThreshold() throws Exception {
        when(previousRegistry.size()).thenReturn(10);
        when(currentRegistry.size()).thenReturn(5);
        assertNotReady();
    }

    @Test
    public void testRelaxesOverTime() throws Exception {
        when(previousRegistry.size()).thenReturn(10);
        when(currentRegistry.size()).thenReturn(5);
        assertNotReady();

        scheduler.advanceTimeBy(RELAX_INTERVAL_MS * 29, TimeUnit.MILLISECONDS);
        assertNotReady();

        scheduler.advanceTimeBy(RELAX_INTERVAL_MS, TimeUnit.MILLISECONDS);
        assertReady();
    }

    protected void assertReady() {
        assertThat(strategy.isReadyToSwap(previousRegistry, currentRegistry), is(true));
    }

    protected void assertNotReady() {
        assertThat(strategy.isReadyToSwap(previousRegistry, currentRegistry), is(false));
    }
}