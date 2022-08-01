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

package com.netflix.eureka;

import jakarta.inject.Singleton;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;

/**
 * Filter to check whether the eureka server is ready to take requests based on
 * its {@link InstanceStatus}.
 */
@Singleton
public class StatusFilter implements Filter {

    private static final int SC_TEMPORARY_REDIRECT = 307;

    /*
     * (non-Javadoc)
     *
     * @see jakarta.servlet.Filter#destroy()
     */
    public void destroy() {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     *
     * @see jakarta.servlet.Filter#doFilter(jakarta.servlet.ServletRequest,
     * jakarta.servlet.ServletResponse, jakarta.servlet.FilterChain)
     */
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        InstanceInfo myInfo = ApplicationInfoManager.getInstance().getInfo();
        InstanceStatus status = myInfo.getStatus();
        if (status != InstanceStatus.UP && response instanceof HttpServletResponse) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.sendError(SC_TEMPORARY_REDIRECT,
                    "Current node is currently not ready to serve requests -- current status: "
                            + status + " - try another DS node: ");
        }
        chain.doFilter(request, response);
    }

    /*
     * (non-Javadoc)
     *
     * @see jakarta.servlet.Filter#init(jakarta.servlet.FilterConfig)
     */
    public void init(FilterConfig arg0) throws ServletException {
    }

}
