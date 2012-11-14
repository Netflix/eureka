package com.netflix.blitz4j;

import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggerFactory;

/**
 * A Category factory that overrides log4j to provide a lock free implementation
 * 
 * @author kranganathan
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
