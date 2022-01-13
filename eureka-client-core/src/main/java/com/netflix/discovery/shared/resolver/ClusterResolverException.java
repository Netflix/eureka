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

package com.netflix.discovery.shared.resolver;

/**
 * @author Tomasz Bak
 */
public class ClusterResolverException extends RuntimeException {

    private static final long serialVersionUID = -3027237921418208925L;

    public ClusterResolverException(String message) {
        super(message);
    }

    public ClusterResolverException(String message, Throwable cause) {
        super(message, cause);
    }
}
