package com.netflix.eureka;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import com.google.common.base.Strings;
import com.netflix.appinfo.AbstractEurekaIdentity;
import com.netflix.servo.monitor.DynamicCounter;
import com.netflix.servo.monitor.MonitorConfig;

/**
 * An auth filter for client requests. For now, it only logs supported client identification data from header info
 */
public class ServerRequestAuthFilter implements Filter {
    public static final String UNKNOWN = "unknown";

    private static final String NAME_PREFIX = "DiscoveryServerRequestAuth_Name_";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // nothing to do here
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        logAuth(request);
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // nothing to do here
    }

    protected void logAuth(ServletRequest request) {
        if (EurekaServerConfigurationManager.getInstance().getConfiguration().shouldLogIdentityHeaders()) {
            if (request instanceof HttpServletRequest) {
                HttpServletRequest httpRequest = (HttpServletRequest) request;

                String clientName = getHeader(httpRequest, AbstractEurekaIdentity.AUTH_NAME_HEADER_KEY);
                String clientVersion = getHeader(httpRequest, AbstractEurekaIdentity.AUTH_VERSION_HEADER_KEY);

                DynamicCounter.increment(MonitorConfig.builder(NAME_PREFIX + clientName + "-" + clientVersion).build());
            }
        }
    }

    protected String getHeader(HttpServletRequest request, String headerKey) {
        String value = request.getHeader(headerKey);
        return Strings.isNullOrEmpty(value) ? UNKNOWN : value;
    }
}
