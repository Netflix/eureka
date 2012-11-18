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

import java.util.HashMap;
import java.util.Map;

/**
 * A simple singleton factory class that finds the batchers by name. Batchers are also created by the factory if
 * needed. The users of the batcher have to simply override the {@link com.netflix.logging.messaging.MessageProcessor}
 * to specify what to do with the batched messages.
 * 
 * It is the user's responsibility to make sure the name is unique (ie) a FQCN would be ideal for a name. The user
 * should also remove the batcher from the cache during shutdown or when they do not need it.
 * 
 * The methods are not synchronized for performance reasons and there is very little downside of not synchronizing it
 * as the last put wins and the already existing objects are garbage collected.
 * 
 * 
 * @author Karthik Ranganathan
 *
 */
public class BatcherFactory {
	private static BatcherFactory batcherFactory = new BatcherFactory();

	// List of all batchers cached
	private static Map<String, MessageBatcher> batcherMap = new HashMap<String, MessageBatcher>();;

	
    /**
     * Get a batcher by name
     * @param name - The name of the batcher 
     * @return - the batcher associated with the name
     */
	public static MessageBatcher getBatcher(String name) {
		MessageBatcher batcher = batcherMap.get(name);
		return batcher;
	}

	
	/**
	 * Creates the batcher. The user needs to make sure another batcher already exists before
	 * they create one.
	 * 
	 * @param name - The name of the batcher to be created
	 * @param processor - The user override for actions to be performed on the batched messages.
	 * @return
	 */
	public static MessageBatcher createBatcher(String name,
			MessageProcessor processor) {
		MessageBatcher batcher = batcherMap.get(name);
		if (batcher == null) {
			synchronized (BatcherFactory.class) {
				batcher = batcherMap.get(name);
				if (batcher == null) {
					batcher = new MessageBatcher(name, processor);
					batcherMap.put(name, batcher);
				}
			}
		}
		return batcher;
	}

	/**
	 * Removes the batcher from the cache.
	 * @param name - The name of the batcher to be removed
	 */
	public static void removeBatcher(String name) {
		batcherMap.remove(name);
	}
}
