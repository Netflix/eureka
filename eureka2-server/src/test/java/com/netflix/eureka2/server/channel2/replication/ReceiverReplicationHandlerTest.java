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

package com.netflix.eureka2.server.channel2.replication;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.channel2.ChannelHandlers;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.subjects.PublishSubject;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 */
public class ReceiverReplicationHandlerTest {

    private final Source clientSource = InstanceModel.getDefaultModel().createSource(Source.Origin.REPLICATED, "testReplicationClient");

    private final EurekaRegistry<InstanceInfo> registry = mock(EurekaRegistry.class);
    private final List<ChangeNotification<InstanceInfo>> registryReceivedUpdates = new ArrayList<>();

    private final ReceiverReplicationHandler handler = new ReceiverReplicationHandler(registry);
    private final PublishSubject<ChannelNotification<ChangeNotification<InstanceInfo>>> inputSubject = PublishSubject.create();

    private final ExtTestSubscriber<ChannelNotification<Void>> testSubscriber = new ExtTestSubscriber<>();

    private long idCounter;

    private Source.SourceMatcher lastEvictionMatcher;


    @Before
    public void setUp() throws Exception {
        when(registry.connect(any(), any())).thenAnswer(invocation -> {
            Source source = (Source) invocation.getArguments()[0];
            if(source == null) {
                throw new IllegalArgumentException("replication source not set");
            }
            Observable<ChangeNotification<InstanceInfo>> updates = (Observable<ChangeNotification<InstanceInfo>>) invocation.getArguments()[1];

            return updates.doOnNext(next -> registryReceivedUpdates.add(next)).ignoreElements().cast(Void.class);
        });

        when(registry.evictAll(any())).thenAnswer(invocation -> {
            lastEvictionMatcher = (Source.SourceMatcher) invocation.getArguments()[0];
            return registryReceivedUpdates.size();
        });
    }

    @Test
    public void testReplicationLifecycle() throws Exception {
        handler.handle(inputSubject).subscribe(testSubscriber);

        // Data replication
        inputSubject.onNext(injectSource(ChannelNotification.newData(nextChange())));
        inputSubject.onNext(ChannelNotification.newData(nextChange()));
        testSubscriber.assertOpen();

        assertThat(registryReceivedUpdates.size(), is(equalTo(2)));
        assertThat(lastEvictionMatcher, is(nullValue()));

        // Buffer marker, that should trigger eviction process
        inputSubject.onNext(ChannelNotification.newData(StreamStateNotification.bufferEndNotification(Interests.forFullRegistry())));
        assertThat(lastEvictionMatcher, is(notNullValue()));
    }

    private ChannelNotification<ChangeNotification<InstanceInfo>> injectSource(ChannelNotification<ChangeNotification<InstanceInfo>> channelNotification) {
        return ChannelHandlers.setClientSource(channelNotification, clientSource);
    }

    private ChangeNotification<InstanceInfo> nextChange() {
        InstanceInfo instance = SampleInstanceInfo.Backend.builder().withId(Long.toString(idCounter++)).build();
        return new ChangeNotification<>(ChangeNotification.Kind.Add, instance);
    }
}