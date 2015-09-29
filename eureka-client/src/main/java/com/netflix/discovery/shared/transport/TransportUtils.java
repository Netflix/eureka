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

package com.netflix.discovery.shared.transport;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Tomasz Bak
 */
public final class TransportUtils {

    private TransportUtils() {
    }

    public static EurekaHttpClient getOrSetAnotherClient(AtomicReference<EurekaHttpClient> eurekaHttpClientRef, EurekaHttpClient another) {
        EurekaHttpClient existing = eurekaHttpClientRef.get();
        if (eurekaHttpClientRef.compareAndSet(null, another)) {
            return another;
        }
        another.shutdown();
        return existing;
    }

    public static void shutdown(EurekaHttpClient eurekaHttpClient) {
        if (eurekaHttpClient != null) {
            eurekaHttpClient.shutdown();
        }
    }
}
