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

package com.netflix.blitz4j;

import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggerFactory;

/**
 * A Category factory that overrides log4j to provide a less contended
 * implementation.
 * 
 * @author Karthik Ranganathan
 * 
 */
public class NFCategoryFactory implements LoggerFactory {

    public NFCategoryFactory() {
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.log4j.spi.LoggerFactory#makeNewLoggerInstance(java.lang.String
     * )
     */
    @Override
    public Logger makeNewLoggerInstance(String name) {
        return new NFLockFreeLogger(name);
    }
}
