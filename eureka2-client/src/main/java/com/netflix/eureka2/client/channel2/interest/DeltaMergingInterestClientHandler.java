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

package com.netflix.eureka2.client.channel2.interest;

import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ModifyNotification;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.utils.functions.RxFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Convert modify notifications to add notifications, merging deltas with internally maintained {@link InstanceInfo}
 * objects.
 */
public class DeltaMergingInterestClientHandler implements InterestHandler {

    private static final Logger logger = LoggerFactory.getLogger(DeltaMergingInterestClientHandler.class);

    private ChannelContext<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> channelContext;

    @Override
    public void init(ChannelContext<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> channelContext) {
        if (!channelContext.hasNext()) {
            throw new IllegalStateException("MergingInterestClientHandler expects next handler in the pipeline");
        }
        this.channelContext = channelContext;
    }

    @Override
    public Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> handle(Observable<ChannelNotification<Interest<InstanceInfo>>> interests) {
        return Observable.create(subscriber -> {
            logger.debug("Subscription to DeltaMergingInterestClientHandler started");

            MergeExecutor mergeExecutor = new MergeExecutor();
            channelContext.next()
                    .handle(interests)
                    .map(next -> {
                        if (next.getKind() != ChannelNotification.Kind.Data) {
                            logger.debug("Forwarding notification of kind {}", next.getKind());
                            return next;
                        }
                        ChangeNotification<InstanceInfo> merged = mergeExecutor.merge(next.getData());
                        if (merged == null) {
                            return null;
                        }
                        return ChannelNotification.newData(merged);
                    })
                    .filter(RxFunctions.filterNullValuesFunc())
                    .subscribe(subscriber);
        });
    }

    static class MergeExecutor {

        /**
         * A local copy of instances received by this channel from the server. This is used for:
         * <p>
         * <ul>
         * <li><i>Updates on the wire</i>: Since we only get the delta on the wire, we use this map to get the last seen
         * {@link InstanceInfo} and apply the delta on it to get the new {@link InstanceInfo}</li>
         * <li><i>Deletes on the wire</i>: Since we only get the identifier for the instance deleted, we use this map to
         * get the last seen {@link InstanceInfo}</li>
         * </ul>
         */
        private final Map<String, InstanceInfo> idVsInstance = new HashMap<>();

        private ChangeNotification<InstanceInfo> merge(ChangeNotification<InstanceInfo> notification) {
            if (notification.getKind() == ChangeNotification.Kind.Add) {
                String id = notification.getData().getId();
                idVsInstance.put(id, notification.getData());

                logger.debug("'Add' change notification received for instance {}", id);

                return notification;
            }
            if (notification.getKind() != ChangeNotification.Kind.Modify) {
                logger.debug("Forwarding ChangeNotification of kind {}", notification.getKind());
                return notification;
            }

            ModifyNotification<InstanceInfo> modify = (ModifyNotification<InstanceInfo>) notification;
            Set<Delta<?>> deltas = modify.getDelta();

            if (deltas.isEmpty()) {
                logger.warn("Empty modify notification");
                return null;
            }

            String id = deltas.iterator().next().getId();
            InstanceInfo instance = idVsInstance.get(id);
            if (instance == null) {
                logger.warn("Received modify notification for unknown instance {}", id);
                return null;
            }
            for (Delta<?> delta : deltas) {
                instance = instance.applyDelta(delta);
            }
            logger.debug("Converted modify to add notification for instance {}", id);
            return new ChangeNotification<>(ChangeNotification.Kind.Add, instance);
        }
    }
}
