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

package com.netflix.eureka.transport;

/**
 * @author Tomasz Bak
 */
public class UserContentWithAck extends UserContent {
    private final String correlationId;
    private final long timeout;

    // For dynamic object creation
    protected UserContentWithAck() {
        correlationId = null;
        timeout = 0;
    }

    public UserContentWithAck(Object body, String correlationId, long timeout) {
        super(body);
        this.correlationId = correlationId;
        this.timeout = timeout;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public long getTimeout() {
        return timeout;
    }
}
