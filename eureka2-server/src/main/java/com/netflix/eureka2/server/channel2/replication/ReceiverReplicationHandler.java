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

import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.channel.ChannelHandlers;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ReplicationHandler;
import com.netflix.eureka2.utils.rx.LoggingSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import static com.netflix.eureka2.utils.rx.PeekOperator.peek;

/**
 */
public class ReceiverReplicationHandler implements ReplicationHandler {

    private static final Logger logger = LoggerFactory.getLogger(ReceiverReplicationHandler.class);

    private static final IllegalStateException REPLICATION_SOURCE_NOT_FOUND = new IllegalStateException("Replication source not found");

    private final EurekaRegistry<InstanceInfo> registry;

    public ReceiverReplicationHandler(EurekaRegistry<InstanceInfo> registry) {
        this.registry = registry;
    }

    @Override
    public void init(ChannelContext<ChangeNotification<InstanceInfo>, Void> channelContext) {
        if (channelContext.hasNext()) {
            throw new IllegalStateException("ReceiverReplicationChannel must be the last one in the pipeline");
        }
    }

    @Override
    public Observable<ChannelNotification<Void>> handle(Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> inputStream) {
        return inputStream.compose(peek((head, stream) -> {
            Source replicationSource = ChannelHandlers.getClientSource(head);
            if(replicationSource == null) {
                return Observable.error(REPLICATION_SOURCE_NOT_FOUND);
            }

            Observable<ChangeNotification<InstanceInfo>> replicationUpdates = stream
                    .filter(next -> next.getKind() == ChannelNotification.Kind.Data)
                    .map(channelNotification -> channelNotification.getData())
                    .doOnNext(change -> {
                        if(change instanceof StreamStateNotification) {
                            StreamStateNotification<InstanceInfo> stateChange = (StreamStateNotification<InstanceInfo>) change;
                            if (stateChange.getBufferState() == StreamStateNotification.BufferState.BufferEnd) {
                                registry.evictAll(new EvictSourceMatcher(replicationSource)).subscribe(new LoggingSubscriber<Long>(logger));
                            }
                        }
                    });

            Observable cast = registry.connect(replicationSource, replicationUpdates);
            return (Observable<ChannelNotification<Void>>) cast;
        }));
    }

    private static class EvictSourceMatcher extends Source.SourceMatcher {

        private final Source currentSource;

        EvictSourceMatcher(Source currentSource) {
            this.currentSource = currentSource;
        }

        @Override
        public boolean match(Source another) {
            return another.getOrigin() == currentSource.getOrigin() &&
                    another.getName().equals(currentSource.getName()) &&
                    another.getId() < currentSource.getId();
        }

        @Override
        public String toString() {
            return "evictAllOlderMatcher{" + currentSource + '}';
        }
    }
}
