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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.eureka2.server.audit.AuditRecord;
import com.netflix.eureka2.server.audit.AuditService;
import com.netflix.eureka2.server.audit.kafka.config.KafkaAuditServiceConfig;
import com.netflix.eureka2.server.spi.ExtensionContext;
import com.netflix.eureka2.utils.Json;
import com.netflix.eureka2.utils.StreamedDataCollector;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

/**
 * {@link AuditService} implementation with persistence to Kafka. {@link AuditRecord} is encoded
 * in JSON format. {@link ExtensionContext#getEurekaClusterName()} is used as a default topic name.
 *
 * @author Tomasz Bak
 */
@Singleton
public class KafkaAuditService implements AuditService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaAuditService.class);

    private static final Exception QUEUE_FULL_ERROR = new Exception("Kafka audit queue full");

    private final String topic;
    private final StreamedDataCollector<InetSocketAddress> serverSource;
    private final Worker worker;
    private final KafkaAuditServiceConfig config;

    /* Access from test */ volatile Producer<String, byte[]> kafkaProducer;
    private final ConcurrentLinkedQueue<AuditRecord> auditRecordQueue = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean processorScheduled = new AtomicBoolean();
    private final Action0 processorAction = new Action0() {
        @Override
        public void call() {
            processorScheduled.set(false);
            while (!auditRecordQueue.isEmpty()) {
                Producer<String, byte[]> currentKafkaProducer = kafkaProducer;
                if (currentKafkaProducer != null) {
                    AuditRecord record = auditRecordQueue.peek();
                    try {
                        ProducerRecord<String, byte[]> message = new ProducerRecord<>(topic, Json.toByteArrayJson(record));
                        kafkaProducer.send(message);
                        auditRecordQueue.poll();
                    } catch (Exception e) {
                        logger.error("Kafka message send error; reconnecting", e);
                        kafkaProducer = null;
                        scheduleReconnect();
                        return;
                    }
                } else {
                    return;
                }
            }
        }
    };

    @Inject
    public KafkaAuditService(ExtensionContext context,
                             KafkaAuditServiceConfig config,
                             KafkaServersProvider kafkaServersProvider) {
        this(context, config, kafkaServersProvider, Schedulers.io());
    }

    public KafkaAuditService(ExtensionContext context,
                             KafkaAuditServiceConfig config,
                             KafkaServersProvider kafkaServersProvider,
                             Scheduler scheduler) {
        validateConfiguration(config);
        this.config = config;
        this.topic = config.getKafkaTopic() == null ? context.getEurekaClusterName() : config.getKafkaTopic();
        this.serverSource = kafkaServersProvider.get();
        this.worker = scheduler.createWorker();
    }

    private void validateConfiguration(KafkaAuditServiceConfig config) {
        if (config.getServerList() == null) {
            if (config.getKafkaVip() == null) {
                throw new IllegalArgumentException("No static server list defined, and kafka vip not defined via property");
            }
            if (config.getKafkaPort() == 0) {
                throw new IllegalArgumentException("No static server list defined, and kafka port not defined via property");
            }
        }
    }

    @PostConstruct
    public void start() {
        reconnect();
    }

    @PreDestroy
    public void stop() {
        worker.unsubscribe();
        serverSource.close();
        if (kafkaProducer != null) {
            kafkaProducer.close();
            kafkaProducer = null;
        }
    }

    @Override
    public Observable<Void> write(AuditRecord record) {
        if (auditRecordQueue.size() < config.getMaxQueueSize()) {
            auditRecordQueue.add(record);
            if (kafkaProducer != null && processorScheduled.compareAndSet(false, true)) {
                scheduleQueueProcessing();
            }
        } else {
            logger.warn("Kafka audit queue full; dropping audit message {}", record);
            return Observable.error(QUEUE_FULL_ERROR);
        }
        return Observable.empty();
    }

    private void reconnect() {
        List<InetSocketAddress> addresses = serverSource.latestSnapshot();
        if (addresses.isEmpty()) {
            scheduleReconnect();
        } else {
            kafkaProducer = createKafkaProducer(addresses);
            scheduleQueueProcessing();
        }
    }

    private void scheduleReconnect() {
        worker.schedule(new Action0() {
            @Override
            public void call() {
                reconnect();
            }
        }, config.getRetryIntervalMs(), TimeUnit.MILLISECONDS);
    }

    private void scheduleQueueProcessing() {
        worker.schedule(processorAction);
    }

    private static Producer<String, byte[]> createKafkaProducer(List<InetSocketAddress> addresses) {
        StringBuilder sb = new StringBuilder();
        for (InetSocketAddress server : addresses) {
            sb.append(server.getHostString()).append(':').append(server.getPort()).append(',');
        }
        String serverProperty = sb.substring(0, sb.length() - 1);

        Properties props = new Properties();
        props.setProperty(ProducerConfig.CLIENT_ID_CONFIG, "eureka2");
        props.setProperty(ProducerConfig.ACKS_CONFIG, "0");

        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, serverProperty);
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");

        return new KafkaProducer<>(props);
    }
}
