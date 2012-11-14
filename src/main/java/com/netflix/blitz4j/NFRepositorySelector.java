package com.netflix.blitz4j;

import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.RepositorySelector;

/**
 * 
 * A Repository Selector class that overrides log4j to provide a lock free implementation
 * @author kranganathan
 *
 */
public class NFRepositorySelector implements RepositorySelector {

    final LoggerRepository repository;

    public NFRepositorySelector(LoggerRepository repository) {
        this.repository = repository;
    }

    public LoggerRepository getLoggerRepository() {
        return repository;
    }
}
