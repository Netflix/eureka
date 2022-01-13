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

import com.netflix.discovery.shared.transport.decorator.EurekaHttpClientDecorator.RequestType;

/**
 * @author Tomasz Bak
 */
public final class ServerStatusEvaluators {

    private static final ServerStatusEvaluator LEGACY_EVALUATOR = new ServerStatusEvaluator() {
        @Override
        public boolean accept(int statusCode, RequestType requestType) {
            if (statusCode >= 200 && statusCode < 300 || statusCode == 302) {
                return true;
            } else if (requestType == RequestType.Register && statusCode == 404) {
                return true;
            } else if (requestType == RequestType.SendHeartBeat && statusCode == 404) {
                return true;
            } else if (requestType == RequestType.Cancel) {  // cancel is best effort
                return true;
            } else if (requestType == RequestType.GetDelta && (statusCode == 403 || statusCode == 404)) {
                return true;
            }
            return false;
        }
    };

    private static final ServerStatusEvaluator HTTP_SUCCESS_EVALUATOR = new ServerStatusEvaluator() {
        @Override
        public boolean accept(int statusCode, RequestType requestType) {
            return statusCode >= 200 && statusCode < 300;
        }
    };


    private ServerStatusEvaluators() {
    }

    /**
     * Evaluation rules implemented in com.netflix.discovery.DiscoveryClient#isOk(...) method.
     */
    public static ServerStatusEvaluator legacyEvaluator() {
        return LEGACY_EVALUATOR;
    }

    /**
     * An evaluator that only care about http 2xx responses
     */
    public static ServerStatusEvaluator httpSuccessEvaluator() {
        return HTTP_SUCCESS_EVALUATOR;
    }
}
