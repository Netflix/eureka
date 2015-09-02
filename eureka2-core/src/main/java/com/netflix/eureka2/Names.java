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

package com.netflix.eureka2;

/**
 * @author Tomasz Bak
 */
public final class Names {

    public static final String EUREKA_HTTP = "http";
    public static final String EUREKA_HTTPS = "https";

    public static final String REGISTRATION = "registration";
    public static final String INTEREST = "interest";
    public static final String REPLICATION = "replication";

    public static final String REGISTRATION_CLIENT = "registrationClient";
    public static final String INTEREST_CLIENT = "interestClient";

    public static final String REGISTRATION_SERVER = "registrationServer";
    public static final String INTEREST_SERVER = "interestServer";
    private Names() {
    }
}
