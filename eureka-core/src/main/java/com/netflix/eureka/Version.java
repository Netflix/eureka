/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.eureka;


/**
 * Supported versions for Eureka.
 *
 * <p>The latest versions are always recommended.</p>
 *
 * @author Karthik Ranganathan, Greg Kim
 *
 */
public enum Version {
    V1, V2;

    public static Version toEnum(String v) {
        for (Version version : Version.values()) {
            if (version.name().equalsIgnoreCase(v)) {
                return version;
            }
        }
        //Defaults to v2
        return V2;
    }
}
