/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.discovery.shared.transport.decorator;

/**
 * @author Tomasz Bak
 */
public final class ServerStatusEvaluators {

    private static final ServerStatusEvaluator ALWAYS_ABANDON_EVALUATOR = new ServerStatusEvaluator() {
        @Override
        public boolean abandon(int statusCode) {
            return true;
        }
    };

    private static final ServerStatusEvaluator NEVER_ABANDON_EVALUATOR = new ServerStatusEvaluator() {
        @Override
        public boolean abandon(int statusCode) {
            return false;
        }
    };

    private ServerStatusEvaluators() {
    }

    /**
     * Always abandon a server that returns 5xx. It should be used with the registration requests.
     */
    public static ServerStatusEvaluator alwaysAbandon() {
        return ALWAYS_ABANDON_EVALUATOR;
    }

    /**
     * Never abandon a server that returns 5xx. It should be used with the data fetch requests.
     * This might require an update in the future, where after a few retries/time period, the server is
     * abandoned anyway.
     */
    public static ServerStatusEvaluator neverAbandon() {
        return NEVER_ABANDON_EVALUATOR;
    }
}
