package com.netflix.blitz4j;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.log4j.Appender;
import org.apache.log4j.Category;
import org.apache.log4j.Hierarchy;
import org.apache.log4j.Logger;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.HierarchyEventListener;
import org.apache.log4j.spi.LoggerFactory;

/**
 * A Hierarchy class that overrides log4j to provide a lock free implementation
 * 
 * @author kranganathan
 * 
 */
public class NFHierarchy extends Hierarchy {
    private LoggerFactory myFactory;
    private AbstractQueue<HierarchyEventListener> listeners;

    public NFHierarchy(Logger root) {
       super(root);
        myFactory = new NFCategoryFactory();
        listeners = new ConcurrentLinkedQueue<HierarchyEventListener>();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.log4j.Hierarchy#getLogger(java.lang.String)
     */
    @Override
    public Logger getLogger(String name) {
        return getLogger(name, myFactory);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.log4j.Hierarchy#addHierarchyEventListener(org.apache.log4j
     * .spi.HierarchyEventListener)
     */
    @Override
    public void addHierarchyEventListener(HierarchyEventListener listener) {
        if (listeners.contains(listener)) {
            LogLog.warn("Ignoring attempt to add an existent listener.");
        } else {
            listeners.add(listener);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.log4j.Hierarchy#fireAddAppenderEvent(org.apache.log4j.Category
     * , org.apache.log4j.Appender)
     */
    @Override
    public void fireAddAppenderEvent(Category logger, Appender appender) {
        if (listeners != null) {
            HierarchyEventListener listener;
            for (Iterator<HierarchyEventListener> it = listeners.iterator(); it
            .hasNext();) {
                listener = it.next();
                listener.addAppenderEvent(logger, appender);
            }
        }
    }

    public void fireRemoveAppenderEvent(Category logger, Appender appender) {
        if (listeners != null) {
            HierarchyEventListener listener;
            for (Iterator<HierarchyEventListener> it = listeners.iterator(); it
            .hasNext();) {
                listener = it.next();
                listener.removeAppenderEvent(logger, appender);
            }
        }

    }
    
    
    public void setLoggerFactory(LoggerFactory loggerFactory) {
        this.myFactory = loggerFactory;
    }
}
