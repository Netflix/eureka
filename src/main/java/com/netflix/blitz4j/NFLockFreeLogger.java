package com.netflix.blitz4j;

import java.util.Enumeration;
import java.util.Vector;

import org.apache.log4j.Appender;
import org.apache.log4j.Category;
import org.apache.log4j.Hierarchy;
import org.apache.log4j.Logger;
import org.apache.log4j.helpers.AppenderAttachableImpl;
import org.apache.log4j.helpers.NullEnumeration;
import org.apache.log4j.spi.HierarchyEventListener;
import org.apache.log4j.spi.LoggingEvent;

/**
 * A Logger class that overrides log4j to provide a lock free implementation
 * 
 * @author kranganathan
 * 
 */
public class NFLockFreeLogger extends Logger {

    AppenderAttachableImpl aai;

    protected NFLockFreeLogger(String name) {
        super(name);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.log4j.Category#addAppender(org.apache.log4j.Appender)
     */
    @Override
    public void addAppender(Appender newAppender) {
        if (aai == null) {
            synchronized (this) {
                if (aai == null) {
                    aai = new NFAppenderAttachableImpl();
                }
            }
        }
        aai.addAppender(newAppender);
        repository.fireAddAppenderEvent(this, newAppender);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.log4j.Category#callAppenders(org.apache.log4j.spi.LoggingEvent
     * )
     */
    @Override
    public void callAppenders(LoggingEvent event) {
        int writes = 0;

        for (Category c = this; c != null; c = c.getParent()) {
            if (!(NFLockFreeLogger.class.isInstance(c))) {
                continue;
            }
            if (((NFLockFreeLogger) c).aai != null) {
                int appenderWrite = ((NFLockFreeLogger) c).aai
                .appendLoopOnAppenders(event);
                writes += appenderWrite;
            }
            if (!c.getAdditivity()) {
                break;
            }
        }
        if (writes == 0) {
            repository.emitNoAppenderWarning(this);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.log4j.Category#getAllAppenders()
     */
    @Override
    public Enumeration getAllAppenders() {
        if (aai == null)
            return NullEnumeration.getInstance();
        else
            return aai.getAllAppenders();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.log4j.Category#getAppender(java.lang.String)
     */
    @Override
    public Appender getAppender(String name) {

        if (aai == null || name == null)
            return null;

        return aai.getAppender(name);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.log4j.Category#isAttached(org.apache.log4j.Appender)
     */
    @Override
    public boolean isAttached(Appender appender) {
        if (appender == null || aai == null)
            return false;
        else {
            return aai.isAttached(appender);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.log4j.Category#removeAllAppenders()
     */
    @Override
    public void removeAllAppenders() {
        if (aai != null) {
            Vector appenders = new Vector();
            Enumeration iter = aai.getAllAppenders();
            if (iter == null) {
                return;
            }
            while(iter.hasMoreElements()) {
                appenders.add(iter.nextElement());
            }
            aai.removeAllAppenders();
            iter = appenders.elements();
            while (iter.hasMoreElements()) {
                fireRemoveAppenderEvent((Appender) iter.nextElement());
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.log4j.Category#removeAppender(org.apache.log4j.Appender)
     */
    @Override
    public void removeAppender(Appender appender) {
        if (appender == null || aai == null) {
            return;
        }
        boolean wasAttached = aai.isAttached(appender);
        aai.removeAppender(appender);
        if (wasAttached) {
            fireRemoveAppenderEvent(appender);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.log4j.Category#removeAppender(java.lang.String)
     */
    @Override
    public void removeAppender(String name) {
        if (name == null || aai == null) {
            return;
        }
        Appender appender = aai.getAppender(name);
        aai.removeAppender(name);
        if (appender != null) {
            fireRemoveAppenderEvent(appender);
        }
    }

    private void fireRemoveAppenderEvent(final Appender appender) {
        if (appender != null) {
            if (repository instanceof Hierarchy) {
                ((NFHierarchy) repository).fireRemoveAppenderEvent(this,
                        appender);
            } else if (repository instanceof HierarchyEventListener) {
                ((HierarchyEventListener) repository).removeAppenderEvent(this,
                        appender);
            }
        }
    }
}
