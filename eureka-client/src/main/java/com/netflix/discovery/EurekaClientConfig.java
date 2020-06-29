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

package com.netflix.discovery;

import java.util.List;

import javax.annotation.Nullable;

import com.google.inject.ImplementedBy;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;

/**
 * Configuration information required by the eureka clients to register an
 * instance with <em>Eureka</em> server.
 *
 * <p>
 * Most of the required information is provided by the default configuration
 * {@link DefaultEurekaClientConfig}. The users just need to provide the eureka
 * server service urls. The Eureka server service urls can be configured by 2
 * mechanisms
 *
 * 1) By registering the information in the DNS. 2) By specifying it in the
 * configuration.
 * </p>
 *
 *
 * Once the client is registered, users can look up information from
 * {@link EurekaClient} based on <em>virtual hostname</em> (also called
 * VIPAddress), the most common way of doing it or by other means to get the
 * information necessary to talk to other instances registered with
 * <em>Eureka</em>.
 *
 * <p>
 * Note that all configurations are not effective at runtime unless and
 * otherwise specified.
 * </p>
 *
 * @author Karthik Ranganathan
 *
 */
@ImplementedBy(DefaultEurekaClientConfig.class)
public interface EurekaClientConfig {

    /**
     * Indicates how often(in seconds) to fetch the registry information from
     * the eureka server.
     *
     * @return the fetch interval in seconds.
     */
    int getRegistryFetchIntervalSeconds();

    /**
     * Indicates how often(in seconds) to replicate instance changes to be
     * replicated to the eureka server.
     *
     * @return the instance replication interval in seconds.
     */
    int getInstanceInfoReplicationIntervalSeconds();

    /**
     * Indicates how long initially (in seconds) to replicate instance info
     * to the eureka server
     */
    int getInitialInstanceInfoReplicationIntervalSeconds();

    /**
     * Indicates how often(in seconds) to poll for changes to eureka server
     * information.
     *
     * <p>
     * Eureka servers could be added or removed and this setting controls how
     * soon the eureka clients should know about it.
     * </p>
     *
     * @return the interval to poll for eureka service url changes.
     */
    int getEurekaServiceUrlPollIntervalSeconds();

    /**
     * Gets the proxy host to eureka server if any.
     *
     * @return the proxy host.
     */
    String getProxyHost();

    /**
     * Gets the proxy port to eureka server if any.
     *
     * @return the proxy port.
     */
    String getProxyPort();

    /**
     * Gets the proxy user name if any.
     *
     * @return the proxy user name.
     */
    String getProxyUserName();

    /**
     * Gets the proxy password if any.
     *
     * @return the proxy password.
     */
    String getProxyPassword();

    /**
     * Indicates whether the content fetched from eureka server has to be
     * compressed whenever it is supported by the server. The registry
     * information from the eureka server is compressed for optimum network
     * traffic.
     *
     * @return true, if the content need to be compressed, false otherwise.
     * @deprecated gzip content encoding will be always enforced in the next minor Eureka release (see com.netflix.eureka.GzipEncodingEnforcingFilter).
     */
    boolean shouldGZipContent();

    /**
     * Indicates how long to wait (in seconds) before a read from eureka server
     * needs to timeout.
     *
     * @return time in seconds before the read should timeout.
     */
    int getEurekaServerReadTimeoutSeconds();

    /**
     * Indicates how long to wait (in seconds) before a connection to eureka
     * server needs to timeout.
     *
     * <p>
     * Note that the connections in the client are pooled by
     * {@link org.apache.http.client.HttpClient} and this setting affects the actual
     * connection creation and also the wait time to get the connection from the
     * pool.
     * </p>
     *
     * @return time in seconds before the connections should timeout.
     */
    int getEurekaServerConnectTimeoutSeconds();

    /**
     * Gets the name of the implementation which implements
     * {@link BackupRegistry} to fetch the registry information as a fall back
     * option for only the first time when the eureka client starts.
     *
     * <p>
     * This may be needed for applications which needs additional resiliency for
     * registry information without which it cannot operate.
     * </p>
     *
     * @return the class name which implements {@link BackupRegistry}.
     */
    String getBackupRegistryImpl();

    /**
     * Gets the total number of connections that is allowed from eureka client
     * to all eureka servers.
     *
     * @return total number of allowed connections from eureka client to all
     *         eureka servers.
     */
    int getEurekaServerTotalConnections();

    /**
     * Gets the total number of connections that is allowed from eureka client
     * to a eureka server host.
     *
     * @return total number of allowed connections from eureka client to a
     *         eureka server.
     */
    int getEurekaServerTotalConnectionsPerHost();

    /**
     * Gets the URL context to be used to construct the <em>service url</em> to
     * contact eureka server when the list of eureka servers come from the
     * DNS.This information is not required if the contract returns the service
     * urls by implementing {@link #getEurekaServerServiceUrls(String)}.
     *
     * <p>
     * The DNS mechanism is used when
     * {@link #shouldUseDnsForFetchingServiceUrls()} is set to <em>true</em> and
     * the eureka client expects the DNS to configured a certain way so that it
     * can fetch changing eureka servers dynamically.
     * </p>
     *
     * <p>
     * <em>The changes are effective at runtime.</em>
     * </p>
     *
     * @return the string indicating the context {@link java.net.URI} of the eureka
     *         server.
     */
    String getEurekaServerURLContext();

    /**
     * Gets the port to be used to construct the <em>service url</em> to contact
     * eureka server when the list of eureka servers come from the DNS.This
     * information is not required if the contract returns the service urls by
     * implementing {@link #getEurekaServerServiceUrls(String)}.
     *
     * <p>
     * The DNS mechanism is used when
     * {@link #shouldUseDnsForFetchingServiceUrls()} is set to <em>true</em> and
     * the eureka client expects the DNS to configured a certain way so that it
     * can fetch changing eureka servers dynamically.
     * </p>
     *
     * <p>
     * <em>The changes are effective at runtime.</em>
     * </p>
     *
     * @return the string indicating the port where the eureka server is
     *         listening.
     */
    String getEurekaServerPort();

    /**
     * Gets the DNS name to be queried to get the list of eureka servers.This
     * information is not required if the contract returns the service urls by
     * implementing {@link #getEurekaServerServiceUrls(String)}.
     *
     * <p>
     * The DNS mechanism is used when
     * {@link #shouldUseDnsForFetchingServiceUrls()} is set to <em>true</em> and
     * the eureka client expects the DNS to configured a certain way so that it
     * can fetch changing eureka servers dynamically.
     * </p>
     *
     * <p>
     * <em>The changes are effective at runtime.</em>
     * </p>
     *
     * @return the string indicating the DNS name to be queried for eureka
     *         servers.
     */
    String getEurekaServerDNSName();

    /**
     * Indicates whether the eureka client should use the DNS mechanism to fetch
     * a list of eureka servers to talk to. When the DNS name is updated to have
     * additional servers, that information is used immediately after the eureka
     * client polls for that information as specified in
     * {@link #getEurekaServiceUrlPollIntervalSeconds()}.
     *
     * <p>
     * Alternatively, the service urls can be returned
     * {@link #getEurekaServerServiceUrls(String)}, but the users should implement
     * their own mechanism to return the updated list in case of changes.
     * </p>
     *
     * <p>
     * <em>The changes are effective at runtime.</em>
     * </p>
     *
     * @return true if the DNS mechanism should be used for fetching urls, false otherwise.
     */
    boolean shouldUseDnsForFetchingServiceUrls();

    /**
     * Indicates whether or not this instance should register its information
     * with eureka server for discovery by others.
     *
     * <p>
     * In some cases, you do not want your instances to be discovered whereas
     * you just want do discover other instances.
     * </p>
     *
     * @return true if this instance should register with eureka, false
     *         otherwise
     */
    boolean shouldRegisterWithEureka();

    /**
     * Indicates whether the client should explicitly unregister itself from the remote server
     * on client shutdown.
     *
     * @return true if this instance should unregister with eureka on client shutdown, false otherwise
     */
    default boolean shouldUnregisterOnShutdown() {
        return true;
    }

    /**
     * Indicates whether or not this instance should try to use the eureka
     * server in the same zone for latency and/or other reason.
     *
     * <p>
     * Ideally eureka clients are configured to talk to servers in the same zone
     * </p>
     *
     * <p>
     * <em>The changes are effective at runtime at the next registry fetch cycle as specified by
     * {@link #getRegistryFetchIntervalSeconds()}</em>
     * </p>
     *
     * @return true if the eureka client should prefer the server in the same
     *         zone, false otherwise.
     */
    boolean shouldPreferSameZoneEureka();

    /**
     * Indicates whether server can redirect a client request to a backup server/cluster.
     * If set to false, the server will handle the request directly, If set to true, it may
     * send HTTP redirect to the client, with a new server location.
     *
     * @return true if HTTP redirects are allowed
     */
    boolean allowRedirects();

    /**
     * Indicates whether to log differences between the eureka server and the
     * eureka client in terms of registry information.
     *
     * <p>
     * Eureka client tries to retrieve only delta changes from eureka server to
     * minimize network traffic. After receiving the deltas, eureka client
     * reconciles the information from the server to verify it has not missed
     * out some information. Reconciliation failures could happen when the
     * client has had network issues communicating to server.If the
     * reconciliation fails, eureka client gets the full registry information.
     * </p>
     *
     * <p>
     * While getting the full registry information, the eureka client can log
     * the differences between the client and the server and this setting
     * controls that.
     * </p>
     * <p>
     * <em>The changes are effective at runtime at the next registry fetch cycle as specified by
     * {@link #getRegistryFetchIntervalSeconds()}</em>
     * </p>
     *
     * @return true if the eureka client should log delta differences in the
     *         case of reconciliation failure.
     */
    boolean shouldLogDeltaDiff();

    /**
     * Indicates whether the eureka client should disable fetching of delta and
     * should rather resort to getting the full registry information.
     *
     * <p>
     * Note that the delta fetches can reduce the traffic tremendously, because
     * the rate of change with the eureka server is normally much lower than the
     * rate of fetches.
     * </p>
     * <p>
     * <em>The changes are effective at runtime at the next registry fetch cycle as specified by
     * {@link #getRegistryFetchIntervalSeconds()}</em>
     * </p>
     *
     * @return true to enable fetching delta information for registry, false to
     *         get the full registry.
     */
    boolean shouldDisableDelta();

    /**
     * Comma separated list of regions for which the eureka registry information will be fetched. It is mandatory to
     * define the availability zones for each of these regions as returned by {@link #getAvailabilityZones(String)}.
     * Failing to do so, will result in failure of discovery client startup.
     *
     * @return Comma separated list of regions for which the eureka registry information will be fetched.
     * <code>null</code> if no remote region has to be fetched.
     */
    @Nullable
    String fetchRegistryForRemoteRegions();

    /**
     * Gets the region (used in AWS datacenters) where this instance resides.
     *
     * @return AWS region where this instance resides.
     */
    String getRegion();

    /**
     * Gets the list of availability zones (used in AWS data centers) for the
     * region in which this instance resides.
     *
     * <p>
     * <em>The changes are effective at runtime at the next registry fetch cycle as specified by
     * {@link #getRegistryFetchIntervalSeconds()}</em>
     * </p>
     * @param region the region where this instance is deployed.
     *
     * @return the list of available zones accessible by this instance.
     */
    String[] getAvailabilityZones(String region);

    /**
     * Gets the list of fully qualified {@link java.net.URL}s to communicate with eureka
     * server.
     *
     * <p>
     * Typically the eureka server {@link java.net.URL}s carry protocol,host,port,context
     * and version information if any.
     * <code>Example: http://ec2-256-156-243-129.compute-1.amazonaws.com:7001/eureka/v2/</code>
     * <p>
     *
     * <p>
     * <em>The changes are effective at runtime at the next service url refresh cycle as specified by
     * {@link #getEurekaServiceUrlPollIntervalSeconds()}</em>
     * </p>
     * @param myZone the zone in which the instance is deployed.
     *
     * @return the list of eureka server service urls for eureka clients to talk
     *         to.
     */
    List<String> getEurekaServerServiceUrls(String myZone);

    /**
     * Indicates whether to get the <em>applications</em> after filtering the
     * applications for instances with only {@link com.netflix.appinfo.InstanceInfo.InstanceStatus#UP} states.
     *
     * <p>
     * <em>The changes are effective at runtime at the next registry fetch cycle as specified by
     * {@link #getRegistryFetchIntervalSeconds()}</em>
     * </p>
     *
     * @return true to filter, false otherwise.
     */
    boolean shouldFilterOnlyUpInstances();

    /**
     * Indicates how much time (in seconds) that the HTTP connections to eureka
     * server can stay idle before it can be closed.
     *
     * <p>
     * In the AWS environment, it is recommended that the values is 30 seconds
     * or less, since the firewall cleans up the connection information after a
     * few mins leaving the connection hanging in limbo
     * </p>
     *
     * @return time in seconds the connections to eureka can stay idle before it
     *         can be closed.
     */
    int getEurekaConnectionIdleTimeoutSeconds();

    /**
     * Indicates whether this client should fetch eureka registry information from eureka server.
     *
     * @return {@code true} if registry information has to be fetched, {@code false} otherwise.
     */
    boolean shouldFetchRegistry();

    /**
     * If set to true, the {@link EurekaClient} initialization should throw an exception at constructor time
     * if the initial fetch of eureka registry information from the remote servers is unsuccessful.
     *
     * Note that if {@link #shouldFetchRegistry()} is set to false, then this config is a no-op.
     *
     * @return true or false for whether the client initialization should enforce an initial fetch.
     */
    default boolean shouldEnforceFetchRegistryAtInit() {
        return false;
    }

    /**
     * Indicates whether the client is only interested in the registry information for a single VIP.
     *
     * @return the address of the VIP (name:port).
     * <code>null</code> if single VIP interest is not present.
     */
    @Nullable
    String getRegistryRefreshSingleVipAddress();

    /**
     * The thread pool size for the heartbeatExecutor to initialise with
     *
     * @return the heartbeatExecutor thread pool size
     */
    int getHeartbeatExecutorThreadPoolSize();

    /**
     * Heartbeat executor exponential back off related property.
     * It is a maximum multiplier value for retry delay, in case where a sequence of timeouts
     * occurred.
     *
     * @return maximum multiplier value for retry delay
     */
    int getHeartbeatExecutorExponentialBackOffBound();

    /**
     * The thread pool size for the cacheRefreshExecutor to initialise with
     *
     * @return the cacheRefreshExecutor thread pool size
     */
    int getCacheRefreshExecutorThreadPoolSize();

    /**
     * Cache refresh executor exponential back off related property.
     * It is a maximum multiplier value for retry delay, in case where a sequence of timeouts
     * occurred.
     *
     * @return maximum multiplier value for retry delay
     */
    int getCacheRefreshExecutorExponentialBackOffBound();

    /**
     * Get a replacement string for Dollar sign <code>$</code> during serializing/deserializing information in eureka server.
     *
     * @return Replacement string for Dollar sign <code>$</code>.
     */
    String getDollarReplacement();

    /**
     * Get a replacement string for underscore sign <code>_</code> during serializing/deserializing information in eureka server.
     *
     * @return Replacement string for underscore sign <code>_</code>.
     */
    String getEscapeCharReplacement();

    /**
     * If set to true, local status updates via
     * {@link com.netflix.appinfo.ApplicationInfoManager#setInstanceStatus(com.netflix.appinfo.InstanceInfo.InstanceStatus)}
     * will trigger on-demand (but rate limited) register/updates to remote eureka servers
     *
     * @return true or false for whether local status updates should be updated to remote servers on-demand
     */
    boolean shouldOnDemandUpdateStatusChange();

    /**
     * If set to true, the {@link EurekaClient} initialization should throw an exception at constructor time
     * if an initial registration to the remote servers is unsuccessful.
     *
     * Note that if {@link #shouldRegisterWithEureka()} is set to false, then this config is a no-op
     *
     * @return true or false for whether the client initialization should enforce an initial registration
     */
    default boolean shouldEnforceRegistrationAtInit() {
        return false;
    }

    /**
     * This is a transient config and once the latest codecs are stable, can be removed (as there will only be one)
     *
     * @return the class name of the encoding codec to use for the client. If none set a default codec will be used
     */
    String getEncoderName();

    /**
     * This is a transient config and once the latest codecs are stable, can be removed (as there will only be one)
     *
     * @return the class name of the decoding codec to use for the client. If none set a default codec will be used
     */
    String getDecoderName();

    /**
     * @return {@link com.netflix.appinfo.EurekaAccept#name()} for client data accept
     */
    String getClientDataAccept();

    /**
     * To avoid configuration API pollution when trying new/experimental or features or for the migration process,
     * the corresponding configuration can be put into experimental configuration section. Config format is:
     * eureka.experimental.freeFormConfigString
     *
     * @return a property of experimental feature
     */
    String getExperimental(String name);

    /**
     * For compatibility, return the transport layer config class
     *
     * @return an instance of {@link EurekaTransportConfig}
     */
    EurekaTransportConfig getTransportConfig();
}
