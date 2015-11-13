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

import com.netflix.eureka2.model.StdModelsInjector;
import com.netflix.eureka2.server.audit.AuditRecord;
import com.netflix.eureka2.server.audit.kafka.config.KafkaAuditServiceConfig;
import com.netflix.eureka2.testkit.data.builder.SampleAuditRecord;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static com.netflix.eureka2.server.audit.kafka.config.bean.KafkaAuditServiceConfigBean.anKafkaAuditServiceConfig;

/**
 * @author Tomasz Bak
 */
public class KafkaAuditServiceTest {

    static {
        StdModelsInjector.injectStdModels();
    }

    private KafkaAuditService auditService;

    @Before
    public void setUpAuditService() throws Exception {
        KafkaAuditServiceConfig config = anKafkaAuditServiceConfig()
                .withKafkaServerList("localhost:7101")
                .withKafkaTopic("eureka_audit")
                .build();
        KafkaServersProvider kafkaServersProvider = new KafkaServersProvider(null, config);
        auditService = new KafkaAuditService(null, config, kafkaServersProvider);
        auditService.start();
    }

    @After
    public void tearDown() throws Exception {
        if (auditService != null) {
            auditService.stop();
        }
    }

    /**
     * TODO: move this to integration test package, once we have one
     */
    @Test(timeout = 600000)
    @Ignore
    public void testSourcePersistence() throws Exception {
        AuditRecord auditRecord = SampleAuditRecord.ZuulServerAdd.build();
        for (int i = 0; i < 100; i++) {
            auditService.write(auditRecord);
            Thread.sleep(1000);
        }
    }
}