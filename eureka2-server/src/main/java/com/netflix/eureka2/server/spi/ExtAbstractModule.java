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

package com.netflix.eureka2.server.spi;

import com.google.inject.AbstractModule;

/**
 * All extensions must provide modules derived from this class to be discoverable.
 *
 * @author Tomasz Bak
 */
public abstract class ExtAbstractModule extends AbstractModule {

    /*
     * Server profile constants to be used as arguments to ConditionalOnProfile annotation.
     */

    public static final String WRITE_PROFILE = "Write";
    public static final String READ_PROFILE = "Read";
    public static final String DASHBOARD_PROFILE = "Dashboard";
    public static final String BRIDGE_PROFILE = "Bridge";

    public enum ServerType {Write, Read, Bridge, Dashboard}
}
