package com.netflix.eureka2.server.audit.kafka.config.bean;

import com.netflix.eureka2.server.audit.kafka.config.KafkaAuditServiceConfig;

/**
 * @author David Liu
 */
public class KafkaAuditServiceConfigBean implements KafkaAuditServiceConfig {

    private final String kafkaServerList;
    private final String kafkaVip;
    private final String kafkaTopic;
    private final int kafkaPort;
    private final long retryIntervalMs;
    private final int maxQueueSize;

    public KafkaAuditServiceConfigBean(String kafkaServerList,
                                       String kafkaVip,
                                       String kafkaTopic,
                                       int kafkaPort,
                                       long retryIntervalMs,
                                       int maxQueueSize) {
        this.kafkaServerList = kafkaServerList;
        this.kafkaVip = kafkaVip;
        this.kafkaTopic = kafkaTopic;
        this.kafkaPort = kafkaPort;
        this.retryIntervalMs = retryIntervalMs;
        this.maxQueueSize = maxQueueSize;
    }

    @Override
    public String getServerList() {
        return kafkaServerList;
    }

    @Override
    public String getKafkaVip() {
        return kafkaVip;
    }

    @Override
    public String getKafkaTopic() {
        return kafkaTopic;
    }

    @Override
    public int getKafkaPort() {
        return kafkaPort;
    }

    @Override
    public long getRetryIntervalMs() {
        return retryIntervalMs;
    }

    @Override
    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public static Builder anKafkaAuditServiceConfig() {
        return new Builder();
    }

    public static class Builder {
        private String kafkaServerList;
        private String kafkaVip;
        private String kafkaTopic;
        private int kafkaPort = KafkaAuditServiceConfig.DEFAULT_KAFKA_PORT;
        private long retryIntervalMs = KafkaAuditServiceConfig.DEFAULT_RETRY_INTERVAL_MS;
        private int maxQueueSize = KafkaAuditServiceConfig.DEFAULT_MAX_QUEUE_SIZE;

        private Builder() {}

        public Builder withKafkaServerList(String kafkaServerList) {
            this.kafkaServerList = kafkaServerList;
            return this;
        }
        public Builder withKafkaVip(String kafkaVip) {
            this.kafkaVip = kafkaVip;
            return this;
        }
        public Builder withKafkaTopic(String kafkaTopic) {
            this.kafkaTopic = kafkaTopic;
            return this;
        }
        public Builder withKafkaPort(int kafkaPort) {
            this.kafkaPort = kafkaPort;
            return this;
        }
        public Builder withRetryIntervalMs(long retryIntervalMs) {
            this.retryIntervalMs = retryIntervalMs;
            return this;
        }
        public Builder withMaxQueueSize(int maxQueueSize) {
            this.maxQueueSize = maxQueueSize;
            return this;
        }

        public KafkaAuditServiceConfig build() {
            KafkaAuditServiceConfig configBean = new KafkaAuditServiceConfigBean(
                    kafkaServerList, kafkaVip, kafkaTopic, kafkaPort, retryIntervalMs, maxQueueSize);
            return configBean;
        }
    }
}
