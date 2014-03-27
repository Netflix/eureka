package com.netflix.discovery;

/**
 * Applications can implement this interface and register a callback with the
 * {@link DiscoveryClient#registerRefreshCallback(RefreshCallback)}.
 * 
 * <p>
 * Your callback will be invoked every time new data is received from the
 * Eureka server. 
 * </p>
 * 
 * @author Karthik Ranganathan, Greg Kim
 */
public interface RefreshCallback {
    /**
     * Notification of updated eureka data
     * @param client
     */
    void postRefresh(DiscoveryClient client);

}
