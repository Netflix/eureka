/*
 * ErrorEntry.java
 *  
 * $Header: //depot/webapplications/eureka/main/src/com/netflix/discovery/util/ErrorEntry.java#1 $ 
 * $DateTime: 2012/06/18 13:12:02 $
 *
 * Copyright (c) 2007 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;


/**
 * Simple wrapper for errors
 */
public class ErrorEntry  {
    
    private final String _msg;
    private final Throwable _t;
    private final long _timestamp;
    
    public ErrorEntry(String message, Throwable t) {
        _msg = message;
        _t = t;
        _timestamp = System.currentTimeMillis();
    }
    
    public String getMessage() {
        return _msg;
    }
    
    public Throwable getThrowable() {
        return _t;
    }
    
    public long getTimestamp() {
        return _timestamp;
    }
    
    public String getMessageDump() {
        if(_msg != null && !"".equals(_msg)) {
            return _msg +"\n"+ getStackTrace(_t);
        }else {
            return getStackTrace(_t);
        }
    }
    
    private final static String getStackTrace(Throwable t){
        if(t != null){
            Writer result = new StringWriter();
            PrintWriter printWriter = new PrintWriter(result);
            t.printStackTrace(printWriter);
            return result.toString();
        }else {
            return "";
        }
    }
}
