package com.netflix.blitz4j;

import org.apache.log4j.*;
import org.apache.log4j.helpers.LogLog;

/**
 * A Root Logger class that overrides log4j to provide a lock free
 * implementation
 * 
 * @author kranganathan
 * 
 */

public final class NFRootLogger extends NFLockFreeLogger {
    
  
    public NFRootLogger(Level level) {
        super("root");
        setLevel(level);
    }


}
