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

import com.netflix.eureka2.channel.ReplicationHandlerStub;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.server.channel.replication.ReplicationLoopException;
import com.netflix.eureka2.server.channel.replication.SenderReplicationLoopDetectorHandler;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.model.ReplicationClientHello;
import com.netflix.eureka2.spi.model.TransportModel;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Test;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.channel.ChannelTestkit.CLIENT_SOURCE;
import static com.netflix.eureka2.channel.ChannelTestkit.SERVER_SOURCE;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 */
public class SenderReplicationLoopDetectorHandlerTest {

    private final SenderReplicationLoopDetectorHandler handler = new SenderReplicationLoopDetectorHandler(CLIENT_SOURCE);

    private final PublishSubject<ChannelNotification<ChangeNotification<InstanceInfo>>> inputSubject = PublishSubject.create();

    private final ExtTestSubscriber<ChannelNotification<Void>> testSubscriber = new ExtTestSubscriber<>();

    @Test
    public void testReplicationLoopDetection() throws Exception {
        ReplicationHandlerStub nextHandler = new ReplicationHandlerStub(CLIENT_SOURCE);
        new ChannelPipeline<>("loopDetector", handler, nextHandler);

        handler.handle(inputSubject).subscribe(testSubscriber);

        ReplicationClientHello clientHello = TransportModel.getDefaultModel().newReplicationClientHello(CLIENT_SOURCE, 1);
        inputSubject.onNext(ChannelNotification.newHello(clientHello));

        testSubscriber.assertOnError(ReplicationLoopException.class);
    }

    @Test
    public void testAllowRegularConnection() throws Exception {
        ReplicationHandlerStub nextHandler = new ReplicationHandlerStub(SERVER_SOURCE);
        new ChannelPipeline<>("loopDetector", handler, nextHandler);

        handler.handle(inputSubject).subscribe(testSubscriber);

        ReplicationClientHello clientHello = TransportModel.getDefaultModel().newReplicationClientHello(CLIENT_SOURCE, 1);
        inputSubject.onNext(ChannelNotification.newHello(clientHello));

        ChangeNotification<InstanceInfo> addChange = new ChangeNotification<>(ChangeNotification.Kind.Add, SampleInstanceInfo.Backend.build());
        inputSubject.onNext(ChannelNotification.newData(addChange));

        testSubscriber.assertOpen();
        assertThat(nextHandler.getCollectedChanges(), is(equalTo(1)));
    }
}