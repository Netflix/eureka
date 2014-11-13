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

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.netflix.eureka2.server.audit.AuditRecord;
import com.netflix.eureka2.server.audit.AuditService;
import com.netflix.eureka2.server.spi.ExtensionContext;
import com.netflix.eureka2.utils.Json;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;
import rx.Observable;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Properties;

/**
 * {@link AuditService} implementation with persistence to Kafka. {@link AuditRecord} is encoded
 * in JSON format. {@link ExtensionContext#getEurekaClusterName()} is used as a default topic name.
 *
 * @author Tomasz Bak
 */
@Singleton
public class KafkaAuditService implements AuditService {

    private final ServerList<Server> kafkaServerList;
    private final String topic;
    /* Access from test */ volatile Producer<String, byte[]> kafkaProducer;

    @Inject
    public KafkaAuditService(ExtensionContext context, KafkaAuditConfig config, ServerList<Server> kafkaServerList) {
        config.validateConfiguration();
        this.kafkaServerList = kafkaServerList;
        this.topic = config.getKafkaTopic() == null ? context.getEurekaClusterName() : config.getKafkaTopic();
    }

    @PostConstruct
    public void start() {
        StringBuilder sb = new StringBuilder(",");
        for (Server server : kafkaServerList.getInitialListOfServers()) {
            sb.append(server.getHostPort());
        }
        String serverProperty = sb.substring(1);

        Properties props = new Properties();
        props.setProperty("metadata.broker.list", serverProperty);
        props.setProperty("producer.type", "async");
        props.setProperty("request.required.acks", "0");

        kafkaProducer = new Producer<String, byte[]>(new ProducerConfig(props));
    }

    @PreDestroy
    public void stop() {
        if (kafkaProducer != null) {
            kafkaProducer.close();
            kafkaProducer = null;
        }
    }

    @Override
    public Observable<Void> write(AuditRecord record) {
        KeyedMessage<String, byte[]> message = new KeyedMessage<>(topic, Json.toByteArrayJson(record));
        kafkaProducer.send(message);
        return Observable.empty();
    }
}
