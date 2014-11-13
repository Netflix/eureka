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

import com.google.inject.Module;
import com.netflix.eureka2.server.audit.AuditRecord;
import com.netflix.eureka2.server.audit.SampleAuditRecord;
import com.netflix.eureka2.server.spi.ExtAbstractModule;
import com.netflix.eureka2.server.spi.ExtensionContext;
import com.netflix.eureka2.server.spi.ExtensionContext.ExtensionContextBuilder;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ServiceLoader;

import static java.lang.String.format;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Tomasz Bak
 */
public class KafkaAuditServiceTest {

    private KafkaAuditService auditService;

    public void setUpAuditService() throws Exception {
        ExtensionContext context = new ExtensionContextBuilder()
                .withEurekaClusterName("eureka2-test")
                .build();

        String kafkaPropertyValue = System.getProperty(KafkaAuditConfig.KAFKA_SERVERS_KEY);
        if (kafkaPropertyValue == null) {
            fail(format("This is integration test and requires that system property %s is set", KafkaAuditConfig.KAFKA_SERVERS_KEY));
        }
        String kafkaTopic = System.getProperty(KafkaAuditConfig.KAFKA_TOPIC_KEY);
        if (kafkaTopic == null) {
            fail(format("This is integration test and requires that system property %s is set", KafkaAuditConfig.KAFKA_TOPIC_KEY));
        }
        PropertySourcedServerList kafkaServerList = new PropertySourcedServerList(kafkaPropertyValue, KafkaAuditConfig.KAFKA_PORT_DEFAULT);
        KafkaAuditConfig config = new KafkaAuditConfig(kafkaPropertyValue, null, -1, kafkaTopic);
        auditService = new KafkaAuditService(context, config, kafkaServerList);
        auditService.start();
    }

    @After
    public void tearDown() throws Exception {
        if (auditService != null) {
            auditService.stop();
        }
    }

    @Test
    public void testServiceLoadBootstrapping() throws Exception {
        ServiceLoader<ExtAbstractModule> loader = ServiceLoader.load(ExtAbstractModule.class);
        boolean matched = false;
        for (Module module : loader) {
            if (module instanceof KafkaAuditServiceModule) {
                matched = true;
                break;
            }
        }
        assertTrue("Module KafkaAuditServiceModule not found by service loader", matched);
    }

    /**
     * TODO: move this to integration test package, once we have one
     */
    @Test
    @Ignore
    public void testSourcePersistence() throws Exception {
        setUpAuditService();

        AuditRecord auditRecord = SampleAuditRecord.ZuulServerAdd.build();
        auditService.write(auditRecord);
    }
}