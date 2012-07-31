/*
 * GlobalFilter.java
 *  
 * $Header:  $ 
 * $DateTime: $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;

/**
 * Filter to indicate that discovery is not able to take requests based on
 * its {@link InstanceStatus}
 */
public class StatusFilter implements Filter {

    private static final int SC_TEMPORARY_REDIRECT = 307;
    /* (non-Javadoc)
     * @see javax.servlet.Filter#destroy()
     */
    public void destroy() {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest, javax.servlet.ServletResponse, javax.servlet.FilterChain)
     */
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        InstanceInfo myInfo = ApplicationInfoManager.getInstance().getInfo();
        InstanceStatus status = myInfo.getStatus();
        if(status != InstanceStatus.UP && response instanceof HttpServletResponse) {
            HttpServletResponse httpRespone = (HttpServletResponse)response;
            httpRespone.sendError(SC_TEMPORARY_REDIRECT, 
                    "Current node is currently not ready to serve requests -- current status: " + 
                    status + " - try another DS node: " );
        }
        chain.doFilter(request, response);
    }

    /* (non-Javadoc)
     * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
     */
    public void init(FilterConfig arg0) throws ServletException {
        // TODO Auto-generated method stub

    }

}
