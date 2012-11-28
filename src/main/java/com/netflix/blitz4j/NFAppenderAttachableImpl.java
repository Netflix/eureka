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

import java.util.AbstractQueue;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.collections.iterators.IteratorEnumeration;
import org.apache.log4j.Appender;
import org.apache.log4j.helpers.AppenderAttachableImpl;
import org.apache.log4j.spi.AppenderAttachable;
import org.apache.log4j.spi.LoggingEvent;

/**
 * This class overrides log4j implementation to provide appender with less
 * multi-threaded contention.
 * 
 * @author Karthik Ranganathan
 * 
 */
public class NFAppenderAttachableImpl extends AppenderAttachableImpl implements
AppenderAttachable {

    protected AbstractQueue<Appender> appenderList = new ConcurrentLinkedQueue<Appender>();
    private AbstractQueue<String> configuredAppenderList = new ConcurrentLinkedQueue<String>();

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
        // If the appender is already there, add this one to the end before
        // removing the
        // previous one
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
        configuredAppenderList.add(newAppender.getName() != null ? newAppender.getName(): "default");
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
        this.configuredAppenderList.clear();
        if (appenderList != null) {
            Iterator<Appender> it = appenderList.iterator();
            while (it.hasNext()) {
                Appender a = (Appender) it.next();
                String[] asyncAppenders = LoggingConfiguration.getInstance()
                .getConfiguration()
                .getAsyncAppenderImplementationNames();
                // For AsyncAppenders, we won't remove appenders.
                // This call is primarily made during dynamic log4 reconfiguration and we will
                // retain the ability to queue the messages.
                for (String asyncAppender : asyncAppenders) {
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
        configuredAppenderList.remove(appender.getName());
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
                configuredAppenderList.remove(a.getName());
                break;
            }
        }
    }
    
    /**
     * Reconciles the appender list after configuration to ensure that the asynchrnous
     * appenders are not left over after the configuration.  This is needed because the
     * appenders are not cleaned out completely during configuration for it to retain the
     * ability to not messages.
     */
    public void reconcileAppenders() {
        for (Appender appender : appenderList) {
            if (!configuredAppenderList.contains(appender.getName())) {
                appender.close();
                appenderList.remove(appender);
            }
        }
    }
}
