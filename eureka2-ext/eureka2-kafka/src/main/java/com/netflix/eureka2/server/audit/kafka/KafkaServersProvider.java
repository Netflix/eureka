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

package com.netflix.eureka2.server.audit.kafka;

import javax.inject.Inject;
import javax.inject.Provider;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.selector.ServiceSelector;
import com.netflix.eureka2.server.spi.ExtensionContext;
import com.netflix.eureka2.utils.StreamedDataCollector;
import rx.functions.Func1;

/**
 * This class creates observable of Kafka addresses that can be set
 * depending on the provided configuration either directly from a property value
 * ({@link KafkaAuditConfig#kafkaServerList}) or Eureka registry
 * ({@link KafkaAuditConfig#KAFKA_VIP_KEY}).
 *
 * @author Tomasz Bak
 */
public class KafkaServersProvider implements Provider<StreamedDataCollector<InetSocketAddress>> {

    private static final Pattern SEMICOLON_RE = Pattern.compile(";");
    private static final ServiceSelector SELECTOR = ServiceSelector.selectBy().publicIp(true).or().any();

    private final ExtensionContext context;
    private final KafkaAuditConfig config;

    @Inject
    public KafkaServersProvider(ExtensionContext context, KafkaAuditConfig config) {
        this.context = context;
        this.config = config;
    }

    @Override
    public StreamedDataCollector<InetSocketAddress> get() {
        StreamedDataCollector<InetSocketAddress> delegate;
        if (config.getKafkaServerList() == null) {
            delegate = StreamedDataCollector.from(
                    context.getLocalRegistryView().forInterest(Interests.forVips(config.getKafkaVip())),
                    new Func1<InstanceInfo, InetSocketAddress>() {
                        @Override
                        public InetSocketAddress call(InstanceInfo instanceInfo) {
                            return SELECTOR.returnServiceAddress(instanceInfo);
                        }
                    }
            );
        } else {
            delegate = StreamedDataCollector.from(parseServerList());
        }
        return delegate;
    }

    private List<InetSocketAddress> parseServerList() {
        List<InetSocketAddress> servers = new ArrayList<>();
        for (String part : SEMICOLON_RE.split(config.getKafkaServerList())) {
            int idx = part.indexOf(':');
            String host;
            int port;
            if (idx >= 0) {
                host = part.substring(0, idx);
                port = Integer.parseInt(part.substring(idx + 1));
            } else {
                host = part;
                port = config.getKafkaPort();
            }
            servers.add(new InetSocketAddress(host, port));
        }
        return servers;
    }
}
