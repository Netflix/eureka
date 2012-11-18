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

package com.netflix.logging.messaging;

import java.util.List;

/**
 * An interface for handling the batched messages. The implementers need to
 * define what needs to be done with the batched messages.
 * 
 * @author Karthik Ranganathan
 * 
 */
public interface MessageProcessor<T> {

    /**
     * Contract for handling the batched objects.
     * 
     * @param objects
     *            - The list of objects that are batched.
     */
    void process(List<T> objects);

}
