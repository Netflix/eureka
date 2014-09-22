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

package com.netflix.eureka.client.bootstrap;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka.client.ServerResolver.Protocol;
import org.junit.Test;
import rx.Notification;
import rx.functions.Action1;

import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class BufferedServerResolverTest {

    @Test
    public void testBuffering() throws Exception {
        StaticServerResolver<InetSocketAddress> resolver = new StaticServerResolver<>();
        BufferedServerResolver<InetSocketAddress> bufferedResolver = new BufferedServerResolver<>(resolver);
        bufferedResolver.start();

        final CountDownLatch completed = new CountDownLatch(1);
        BufferedServerResolver.completeOnPoolSize(bufferedResolver, 1, 1, TimeUnit.SECONDS)
                .materialize()
                .subscribe(new Action1<Notification<Void>>() {
                    @Override
                    public void call(Notification<Void> notification) {
                        if (notification.isOnCompleted()) {
                            completed.countDown();
                        }
                    }
                });

        assertEquals("Should not complete yet", 1, completed.getCount());
        resolver.addServer(new InetSocketAddress("testA", 123), Protocol.TcpDiscovery);
        assertTrue("Should be done by now", completed.await(30, TimeUnit.SECONDS));

        assertEquals(1, bufferedResolver.currentSnapshot().size());
    }
}