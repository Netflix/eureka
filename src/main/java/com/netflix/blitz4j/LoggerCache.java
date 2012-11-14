package com.netflix.blitz4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;


/**
 * A utility class that pre-caches loggers. This caching would be much more lock-free than log4j
 * caches as it uses a {@link java.util.concurrent.ConcurrentHashMap} instead of {@link java.util.Map}
 * 
 * @author kranganathan
 *
 */
public class LoggerCache {
    private static LoggerCache instance = new LoggerCache();
    private Map<String, Logger> appenderLoggerMap = new ConcurrentHashMap<String, Logger>(5000);
    
  
  private LoggerCache() {
      /*
      appenderLoggerMap.put(RTAAppender.class.getName(), Logger.getLogger(RTAAppender.class));
      appenderLoggerMap.put(RTARequestHandler.class.getName(), Logger.getLogger(RTARequestHandler.class));
      appenderLoggerMap.put(RTARequestCache.class.getName(), Logger.getLogger(RTARequestCache.class));
      appenderLoggerMap.put(LogEntry.class.getName(), Logger.getLogger(LogEntry.class));
      appenderLoggerMap.put(NFLogger.class.getName(), Logger.getLogger(NFLogger.class));
      appenderLoggerMap.put(AsyncAppender.class.getName(), Logger.getLogger(AsyncAppender.class));
      appenderLoggerMap.put(Log4jLoggingAdapter.class.getName(), Logger.getLogger(Log4jLoggingAdapter.class));
      */
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
