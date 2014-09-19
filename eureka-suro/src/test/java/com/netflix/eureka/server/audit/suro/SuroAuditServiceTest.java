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

package com.netflix.eureka.server.audit.suro;

import java.util.ServiceLoader;

import com.google.inject.Module;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class SuroAuditServiceTest {

    @Test
    public void testServiceLoadBootstrapping() throws Exception {
        ServiceLoader<Module> loader = ServiceLoader.load(Module.class);
        boolean matched = false;
        for (Module module : loader) {
            if (module instanceof SuroAuditServiceModule) {
                matched = true;
                break;
            }
        }
        assertTrue("Module SuroAuditServiceModule not found by service loader", matched);
    }
}