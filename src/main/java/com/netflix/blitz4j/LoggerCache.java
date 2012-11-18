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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;


/**
 * The class that caches log4j loggers.
 * 
 * <p>
 * This will be much more contention free than log4j caching since the the cache uses a {@link java.util.concurrent.ConcurrentHashMap} instead of {@link java.util.Map}
 * </p>
 * 
 * @author Karthik Ranganathan
 *
 */
public class LoggerCache {
    private static LoggerCache instance = new LoggerCache();
    private Map<String, Logger> appenderLoggerMap = new ConcurrentHashMap<String, Logger>(5000);
    
  
  private LoggerCache() {
    
  }
  
  public static LoggerCache getInstance() {
      return instance;
  }

  /**
   * Get the logger to be used for the given class. 
   * @param clazz - The class for which the logger needs to be returned
   * @return- The log4j logger object
   */
  public Logger getOrCreateLogger(String clazz) {
      Logger logger = appenderLoggerMap.get(clazz);
      if (logger == null) {
          // If multiple threads do the puts, that is fine as it is a one time thing
          logger = Logger.getLogger(clazz);
          appenderLoggerMap.put(clazz, logger);
      }
      return logger;
  }
  
  public void clearAll() {
      appenderLoggerMap.clear();
  }
}
