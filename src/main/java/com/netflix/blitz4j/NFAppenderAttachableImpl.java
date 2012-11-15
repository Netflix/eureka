package com.netflix.blitz4j;

import java.util.AbstractQueue;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.collections.iterators.IteratorEnumeration;
import org.apache.log4j.Appender;
import org.apache.log4j.helpers.AppenderAttachableImpl;
import org.apache.log4j.spi.AppenderAttachable;
import org.apache.log4j.spi.LoggingEvent;

import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;

/**
 * An AppenderAttacableImpl that overrides log4j to provide a lock free
 * implementation
 * 
 * @author kranganathan
 * 
 */
public class NFAppenderAttachableImpl extends AppenderAttachableImpl implements
AppenderAttachable {

    private static final DynamicStringProperty ASYNC_APPENDER_NAME = DynamicPropertyFactory.getInstance().getStringProperty("blitz4j.asyncAppenders", "com.netflix.blitz4j.AsyncAppender");
    protected AbstractQueue<Appender> appenderList = new ConcurrentLinkedQueue<Appender>();

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.log4j.helpers.AppenderAttachableImpl#addAppender(org.apache
     * .log4j.Appender)
     */
    @Override
    public void addAppender(Appender newAppender) {
        // Null values for newAppender parameter are strictly forbidden.
        if (newAppender == null) {
            return;
        }

        boolean isAppenderPresent = appenderList.contains(newAppender);
        if (isAppenderPresent) {
            appenderList.add(newAppender);
            for (Iterator<Appender> it = appenderList.iterator(); it.hasNext();) {
                Appender appender = it.next();
                if (newAppender.equals(appender)) {
                    appender.close();
                    it.remove();
                    return;
                }
            }
        } else {
            appenderList.add(newAppender);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.log4j.helpers.AppenderAttachableImpl#appendLoopOnAppenders
     * (org.apache.log4j.spi.LoggingEvent)
     */
    @Override
    public int appendLoopOnAppenders(LoggingEvent event) {
        int size = 0;
        Appender appender;
        if (appenderList != null) {
            size = appenderList.size();
            Iterator<Appender> it = appenderList.iterator();
            while (it.hasNext()) {
                appender = (Appender) it.next();
                appender.doAppend(event);
            }
        }
        return size;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.log4j.helpers.AppenderAttachableImpl#getAllAppenders()
     */
    @Override
    public Enumeration getAllAppenders() {
        if (appenderList == null)
            return null;
        else {
            Iterator<Appender> it = appenderList.iterator();
            return new IteratorEnumeration(it);
        }
    }
   
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.log4j.helpers.AppenderAttachableImpl#getAppender(java.lang
     * .String)
     */
    @Override
    public Appender getAppender(String name) {
        if (appenderList == null || name == null)
            return null;
        Appender appender;
        Iterator<Appender> it = appenderList.iterator();
        while (it.hasNext()) {
            appender = (Appender) it.next();
            if (name.equals(appender.getName())) {
                return appender;
            }
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.log4j.helpers.AppenderAttachableImpl#isAttached(org.apache
     * .log4j.Appender)
     */
    @Override
    public boolean isAttached(Appender appender) {
        if (appenderList == null || appender == null)
            return false;
        Appender a;
        Iterator<Appender> it = appenderList.iterator();
        while (it.hasNext()) {
            a = (Appender) it.next();
            if (a == appender) {
                return true;
            }
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.log4j.helpers.AppenderAttachableImpl#removeAllAppenders()
     */
    @Override
    public void removeAllAppenders() {
        if (appenderList != null) {
            Iterator<Appender> it = appenderList.iterator();
            while (it.hasNext()) {
                Appender a = (Appender) it.next();
                String[] asyncAppenders = ASYNC_APPENDER_NAME.get().split(",");
                // For AsyncAppender, we have a chance to not lose logging,as it
                // can be batched
                for(String asyncAppender : asyncAppenders) {
                    if (!(asyncAppender.equals(a.getClass().getName()))) {
                        a.close();
                        it.remove();
                    }
                }
          }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.log4j.helpers.AppenderAttachableImpl#removeAppender(org.apache
     * .log4j.Appender)
     */
    @Override
    public void removeAppender(Appender appender) {
        if (appender == null || appenderList == null)
            return;
        appenderList.remove(appender);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.log4j.helpers.AppenderAttachableImpl#removeAppender(java.lang
     * .String)
     */
    @Override
    public void removeAppender(String name) {
        if (name == null || appenderList == null)
            return;
        Iterator<Appender> it = appenderList.iterator();
        while (it.hasNext()) {
            Appender a = (Appender) it.next();
            if (name.equals(a.getName())) {
                it.remove();
                break;
            }
        }
    }
}
