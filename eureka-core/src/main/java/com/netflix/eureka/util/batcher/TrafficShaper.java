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

package com.netflix.eureka.util.batcher;

import com.netflix.eureka.util.batcher.TaskProcessor.ProcessingResult;

/**
 * {@link TrafficShaper} provides admission control policy prior to dispatching tasks to workers.
 * It reacts to events coming via reprocess requests (transient failures, congestion), and delays the processing
 * depending on this feedback.
 *
 * @author Tomasz Bak
 */
class TrafficShaper {

    private final long congestionRetryDelayMs;
    private final long networkFailureRetryMs;

    private volatile long lastCongestionError;
    private volatile long lastNetworkFailure;

    TrafficShaper(long congestionRetryDelayMs, long networkFailureRetryMs) {
        this.congestionRetryDelayMs = congestionRetryDelayMs;
        this.networkFailureRetryMs = networkFailureRetryMs;
    }

    void registerFailure(ProcessingResult processingResult) {
        if (processingResult == ProcessingResult.Congestion) {
            lastCongestionError = System.currentTimeMillis();
        } else if (processingResult == ProcessingResult.TransientError) {
            lastNetworkFailure = System.currentTimeMillis();
        }
    }

    long grantTransmissionPermit() {
        if (lastCongestionError == -1 || lastNetworkFailure == -1) {
            return 0;
        }
        long now = System.currentTimeMillis();
        if (now - lastCongestionError < congestionRetryDelayMs) {
            return congestionRetryDelayMs - (now - lastCongestionError);
        } else {
            lastCongestionError = -1;
        }

        if (now - lastNetworkFailure < networkFailureRetryMs) {
            return networkFailureRetryMs - (now - lastNetworkFailure);
        }
        lastNetworkFailure = -1;
        return 0;
    }
}
