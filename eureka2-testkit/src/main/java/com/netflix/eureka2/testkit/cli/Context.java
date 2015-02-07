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

package com.netflix.eureka2.testkit.cli;

import java.util.HashMap;
import java.util.Map;

import com.netflix.eureka2.config.EurekaTransportConfig;

/**
 * @author Tomasz Bak
 */
public class Context {

    private final EurekaTransportConfig transportConfig;
    private final Map<Integer, Session> sessionMap = new HashMap<>();

    private Session activeSession;

    public Context(EurekaTransportConfig transportConfig) {
        this.transportConfig = transportConfig;
    }

    public Session createSession() {
        activeSession = new Session(this);
        sessionMap.put(activeSession.getSessionId(), activeSession);
        return activeSession;
    }

    public EurekaTransportConfig getTransportConfig() {
        return transportConfig;
    }

    public Session getActiveSession() {
        return activeSession;
    }

    public void close() {
        for (Session session : sessionMap.values()) {
            session.close();
        }
    }

    public void printStatus() {
        System.out.println("Status report");
        System.out.println("=============");

        System.out.println("Active sessions: " + sessionMap.size());
        for (Session session : sessionMap.values()) {
            session.printStatus();
        }
    }
}
