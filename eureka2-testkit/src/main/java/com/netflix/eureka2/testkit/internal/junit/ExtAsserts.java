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

package com.netflix.eureka2.testkit.internal.junit;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.Assert.fail;

/**
 */
public final class ExtAsserts {

    private static final int _10_MICROSEC_IN_NANOS = 10 * 1000;

    private ExtAsserts() {
    }

    /**
     * Evaluate provided function periodically until it issues expected value. The evaluation time is guarded by a
     * timeout, which if crossed results in assertion error.
     */
    public static <T> void assertThat(Supplier<T> valueSupplier, T expectedValue, long timeout, TimeUnit timeUnit) throws InterruptedException {
        long endTime = timeout <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + timeUnit.toMillis(timeout);

        do {
            T actual = valueSupplier.get();
            if (expectedValue == null && actual == null) {
                return;
            }
            if (expectedValue != null && expectedValue.equals(actual)) {
                return;
            }
            Thread.sleep(0, _10_MICROSEC_IN_NANOS);
        } while (endTime > System.currentTimeMillis());

        fail("Expected value not provided in time; actual value=" + valueSupplier.get() + ", while expected is " + expectedValue);
    }
}
