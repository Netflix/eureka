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

package com.netflix.eureka.server.audit.kafka;

import com.netflix.eureka.server.spi.ExtensionContext;

/**
 * Kafka audit service configuration options.
 *
 * @author Tomasz Bak
 */
public class KafkaAuditConfig {

    public static final String KAFKA_AUDIT_KEYS_PREFIX = "eureka.ext.audit.kafka";

    /**
     * Kafka server list can be injected directly via configuration, in the following format:
     * host[:port][;host[:port...]]
     *
     * This property is an alternative to {@link #KAFKA_VIP_KEY}, with higher priority if both defined.
     */
    public static final String KAFKA_SERVER_LIST_KEY = KAFKA_AUDIT_KEYS_PREFIX + ".servers";

    /**
     * VIP address resolved in local Eureka registry. This property must be set, unless a list of
     * Kafka servers is defined directly with {@link #KAFKA_SERVER_LIST_KEY}.
     */
    public static final String KAFKA_VIP_KEY = KAFKA_AUDIT_KEYS_PREFIX + ".vip";

    /**
     * Kafka server port. Optional parameter with default defined by {@link #KAFKA_PORT_DEFAULT}.
     */
    public static final String KAFKA_PORT_KEY = KAFKA_AUDIT_KEYS_PREFIX + ".port";

    public static final int KAFKA_PORT_DEFAULT = 7101;

    /**
     * Kafka topic for audit messages. If not set, {@link ExtensionContext#getEurekaClusterName()} is used.
     */
    public static final String AUDIT_KAFKA_TOPIC = KAFKA_AUDIT_KEYS_PREFIX + ".topic";

    private final String kafkaServerList;
    private final String kafkaVip;
    private final int kafkaPort;

    public KafkaAuditConfig(ExtensionContext context) {
        kafkaServerList = context.getProperty(KAFKA_SERVER_LIST_KEY);
        if (kafkaServerList == null) {
            kafkaVip = context.getProperty(KAFKA_VIP_KEY);
            if (kafkaVip == null) {
                throw new IllegalArgumentException("Kafka vip not defined via property " + KAFKA_VIP_KEY);
            }
            String portValue = context.getProperty(KAFKA_PORT_KEY);
            if (portValue == null) {
                throw new IllegalArgumentException("Kafka port not defined via property " + KAFKA_PORT_KEY);
            }
            try {
                kafkaPort = Integer.parseInt(portValue);
            } catch (NumberFormatException ignored) {
                throw new IllegalArgumentException("Not an integer value in property " + KAFKA_PORT_KEY);
            }
        } else {
            kafkaVip = null;
            kafkaPort = -1;
        }
    }

    public String getKafkaServerList() {
        return kafkaServerList;
    }

    public String getKafkaVip() {
        return kafkaVip;
    }

    public int getKafkaPort() {
        return kafkaPort;
    }
}
