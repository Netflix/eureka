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

package com.netflix.eureka.client.transport.discovery.asynchronous;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka.client.transport.discovery.DiscoveryClient;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.ChangeNotification.Kind;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.interests.ModifyNotification;
import com.netflix.eureka.protocol.Heartbeat;
import com.netflix.eureka.protocol.discovery.AddInstance;
import com.netflix.eureka.protocol.discovery.DeleteInstance;
import com.netflix.eureka.protocol.discovery.InterestSetNotification;
import com.netflix.eureka.protocol.discovery.RegisterInterestSet;
import com.netflix.eureka.protocol.discovery.UnregisterInterestSet;
import com.netflix.eureka.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.transport.MessageBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class AsyncDiscoveryClient implements DiscoveryClient {

    private static Logger logger = LoggerFactory.getLogger(AsyncDiscoveryClient.class);

    // TODO: we need to cache all instances so we can produce ModifyNotification objects from field-level updates.
    // Can we move it somewhere else? If not we need to maintain this cache properly.
    private Map<String, InstanceInfo> cachedInstances = new ConcurrentHashMap<String, InstanceInfo>();
    private final MessageBroker messageBroker;

    public AsyncDiscoveryClient(MessageBroker messageBroker) {
        this.messageBroker = messageBroker;
    }

    @Override
    public Observable<Void> registerInterestSet(Interest<InstanceInfo> interest) {
        List<Interest<InstanceInfo>> interests = new ArrayList<Interest<InstanceInfo>>();
        interests.add(interest);
        return messageBroker.submitWithAck(new RegisterInterestSet(interests));
    }

    @Override
    public Observable<Void> unregisterInterestSet() {
        return messageBroker.submitWithAck(UnregisterInterestSet.INSTANCE);
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> updates() {
        return messageBroker.incoming().filter(new Func1<Object, Boolean>() {
            @Override
            public Boolean call(Object message) {
                boolean isKnown = message instanceof InterestSetNotification;
                if (!isKnown) {
                    logger.warn("Unrecognized discovery protocol message of type " + message.getClass());
                }
                return isKnown;
            }
        }).map(new Func1<Object, ChangeNotification<InstanceInfo>>() {
            @Override
            public ChangeNotification<InstanceInfo> call(Object message) {
                InterestSetNotification notification = (InterestSetNotification) message;
                if (notification instanceof AddInstance) {
                    InstanceInfo instanceInfo = ((AddInstance) notification).getInstanceInfo();
                    cachedInstances.put(instanceInfo.getId(), instanceInfo);
                    return new ChangeNotification<InstanceInfo>(Kind.Add, instanceInfo);
                }
                if (notification instanceof UpdateInstanceInfo) {
                    UpdateInstanceInfo update = (UpdateInstanceInfo) notification;
                    InstanceInfo instanceInfo = cachedInstances.get(update.getInstanceId());
                    if (instanceInfo != null) {
                        // TODO: add delta once the delta model is merged in
                        return new ModifyNotification<InstanceInfo>(instanceInfo, null);
                    } else {
                        logger.warn("Received update notification for unknown server instance " + update.getInstanceId());
                    }
                } else if (notification instanceof DeleteInstance) {
                    DeleteInstance delete = (DeleteInstance) notification;
                    InstanceInfo instanceInfo = cachedInstances.remove(delete.getInstanceId());
                    if (instanceInfo != null) {
                        return new ChangeNotification<InstanceInfo>(Kind.Delete, instanceInfo);
                    }
                }
                return null;
            }
        });
    }

    @Override
    public Observable<Void> heartbeat() {
        return messageBroker.submit(Heartbeat.INSTANCE);
    }

    @Override
    public void shutdown() {
        messageBroker.shutdown();
    }
}
