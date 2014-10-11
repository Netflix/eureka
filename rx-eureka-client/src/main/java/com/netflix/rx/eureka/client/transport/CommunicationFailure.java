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

package com.netflix.rx.eureka.client.transport;

/**
 * This exception class shall encapsulate all errors reported by transport.
 * The {@link Reason} value specifies if the given transport endpoint should be
 * still usable ('temporary' error) or is broken and should be abandoned.
 *
 * @author Tomasz Bak
 */
public class CommunicationFailure extends RuntimeException {

    public enum Reason {
        Temporary,
        Permanent
    }

    private final Reason reason;

    public CommunicationFailure(String message, Reason reason) {
        super(message);
        this.reason = reason;
    }

    public CommunicationFailure(String message, Reason reason, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
