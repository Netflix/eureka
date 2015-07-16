package com.netflix.eureka2.server.audit.kafka.config;

import com.netflix.archaius.annotations.DefaultValue;

/**
 * @author David Liu
 */
public interface KafkaAuditServiceConfig {

    int DEFAULT_KAFKA_PORT = 7101;
    long DEFAULT_RETRY_INTERVAL_MS = 30000;
    int DEFAULT_MAX_QUEUE_SIZE = 10000;

    /**
     * Kafka server list can be injected directly via configuration, in the following format:
     * host[:port][;host[:port...]]
     *
     * This property is an alternative to {@link #getKafkaVip()}, with higher priority if both defined.
     */
    String getServerList();

    /**
     * VIP address resolved in local Eureka registry. This property must be set, unless a list of
     * Kafka servers is defined directly with {@link #getServerList()}.
     */
    String getKafkaVip();

    /**
     * Kafka topic for audit messages. If not set, Eureka cluster name is used.
     */
    String getKafkaTopic();

    /**
     * Kafka server port. Optional parameter with defaults.
     */
    @DefaultValue("" + DEFAULT_KAFKA_PORT)
    int getKafkaPort();

    /**
     * Kafka reconnect retry interval. Optional parameter with defaults.
     */
    @DefaultValue("" + DEFAULT_RETRY_INTERVAL_MS)
    long getRetryIntervalMs();

    /**
     * Audit record queue size. Optional parameter with defaults.
     */
    @DefaultValue("" + DEFAULT_MAX_QUEUE_SIZE)
    int getMaxQueueSize();
}
