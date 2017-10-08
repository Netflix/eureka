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

package com.netflix.eureka.aws;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.eureka.registry.InstanceRegistry;
import com.netflix.appinfo.DataCenterInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.SuspendedProcess;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A utility class for querying and updating information about amazon
 * autoscaling groups using the AWS APIs.
 *
 * @author Karthik Ranganathan
 *
 */
@Singleton
public class AwsAsgUtil {
    private static final Logger logger = LoggerFactory.getLogger(AwsAsgUtil.class);

    private static final String PROP_ADD_TO_LOAD_BALANCER = "AddToLoadBalancer";

    private static final String accountId = getAccountId();

    private Map<String, Credentials> stsCredentials = new HashMap<String, Credentials>();

    private final ExecutorService cacheReloadExecutor = new ThreadPoolExecutor(
            1, 10, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "Eureka-AWS-isASGEnabled");
                    thread.setDaemon(true);
                    return thread;
                }
    });

    private ListeningExecutorService listeningCacheReloadExecutor = MoreExecutors.listeningDecorator(cacheReloadExecutor);

    // Cache for the AWS ASG information
    private final Timer timer = new Timer("Eureka-ASGCacheRefresh", true);
    private final com.netflix.servo.monitor.Timer loadASGInfoTimer = Monitors.newTimer("Eureka-loadASGInfo");

    private final EurekaServerConfig serverConfig;
    private final EurekaClientConfig clientConfig;
    private final InstanceRegistry registry;
    private final LoadingCache<CacheKey, Boolean> asgCache;
    private final AmazonAutoScaling awsClient;

    @Inject
    public AwsAsgUtil(EurekaServerConfig serverConfig,
                      EurekaClientConfig clientConfig,
                      InstanceRegistry registry) {
        this.serverConfig = serverConfig;
        this.clientConfig = clientConfig;
        this.registry = registry;
        this.asgCache = CacheBuilder
                .newBuilder().initialCapacity(500)
                .expireAfterAccess(serverConfig.getASGCacheExpiryTimeoutMs(), TimeUnit.MILLISECONDS)
                .build(new CacheLoader<CacheKey, Boolean>() {
                    @Override
                    public Boolean load(CacheKey key) throws Exception {
                        return isASGEnabledinAWS(key.asgAccountId, key.asgName);
                    }
                    @Override
                    public ListenableFuture<Boolean> reload(final CacheKey key, Boolean oldValue) throws Exception {
                        return listeningCacheReloadExecutor.submit(new Callable<Boolean>() {
                            @Override
                            public Boolean call() throws Exception {
                                return load(key);
                            }
                        });
                    }
                });

        this.awsClient = getAmazonAutoScalingClient();
        this.awsClient.setEndpoint("autoscaling." + clientConfig.getRegion() + ".amazonaws.com");
        this.timer.schedule(getASGUpdateTask(),
                serverConfig.getASGUpdateIntervalMs(),
                serverConfig.getASGUpdateIntervalMs());

        try {
            Monitors.registerObject(this);
        } catch (Throwable e) {
            logger.warn("Cannot register the JMX monitor :", e);
        }
    }

    /**
     * Return the status of the ASG whether is enabled or disabled for service.
     * The value is picked up from the cache except the very first time.
     *
     * @param instanceInfo the instanceInfo for the lookup
     * @return true if enabled, false otherwise
     */
    public boolean isASGEnabled(InstanceInfo instanceInfo) {
        CacheKey cacheKey = new CacheKey(getAccountId(instanceInfo, accountId), instanceInfo.getASGName());
        Boolean result = asgCache.getIfPresent(cacheKey);
        if (result != null) {
            return result;
        } else {
            logger.info("Cache value for asg {} does not exist yet, async refreshing.", cacheKey.asgName);
            // Only do an async refresh if it does not yet exist. Do this to refrain from calling aws api too much
            asgCache.refresh(cacheKey);
            return true;
        }
    }

    /**
     * Sets the status of the ASG.
     *
     * @param asgName The name of the ASG
     * @param enabled true to enable, false to disable
     */
    public void setStatus(String asgName, boolean enabled) {
        String asgAccountId = getASGAccount(asgName);
        asgCache.put(new CacheKey(asgAccountId, asgName), enabled);
    }

    /**
     * Check if the ASG is disabled. The amazon flag "AddToLoadBalancer" is
     * queried to figure out if it is or not.
     *
     * @param asgName
     *            - The name of the ASG for which the status needs to be queried
     * @return - true if the ASG is disabled, false otherwise
     */
    private boolean isAddToLoadBalancerSuspended(String asgAccountId, String asgName) {
        AutoScalingGroup asg;
        if(asgAccountId == null || asgAccountId.equals(accountId)) {
            asg = retrieveAutoScalingGroup(asgName);
        } else {
            asg = retrieveAutoScalingGroupCrossAccount(asgAccountId, asgName);
        }
        if (asg == null) {
            logger.warn("The ASG information for {} could not be found. So returning false.", asgName);
            return false;
        }
        return isAddToLoadBalancerSuspended(asg);
    }

    /**
     * Checks if the load balancer addition is disabled or not.
     *
     * @param asg
     *            - The ASG object for which the status needs to be checked
     * @return - true, if the load balancer addition is suspended, false
     *         otherwise.
     */
    private boolean isAddToLoadBalancerSuspended(AutoScalingGroup asg) {
        List<SuspendedProcess> suspendedProcesses = asg.getSuspendedProcesses();
        for (SuspendedProcess process : suspendedProcesses) {
            if (PROP_ADD_TO_LOAD_BALANCER.equals(process.getProcessName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Queries AWS to get the autoscaling information given the asgName.
     *
     * @param asgName
     *            - The name of the ASG.
     * @return - The auto scaling group information.
     */
    private AutoScalingGroup retrieveAutoScalingGroup(String asgName) {
        if (Strings.isNullOrEmpty(asgName)) {
            logger.warn("null asgName specified, not attempting to retrieve AutoScalingGroup from AWS");
            return null;
        }
        // You can pass one name or a list of names in the request
        DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest()
                .withAutoScalingGroupNames(asgName);
        DescribeAutoScalingGroupsResult result = awsClient
                .describeAutoScalingGroups(request);
        List<AutoScalingGroup> asgs = result.getAutoScalingGroups();
        if (asgs.isEmpty()) {
            return null;
        } else {
            return asgs.get(0);
        }
    }

    private Credentials initializeStsSession(String asgAccount) {
        AWSSecurityTokenService sts = new AWSSecurityTokenServiceClient(new InstanceProfileCredentialsProvider());
        String region = clientConfig.getRegion();
        if (!region.equals("us-east-1")) {
            sts.setEndpoint("sts." + region + ".amazonaws.com");
        }

        String roleName = serverConfig.getListAutoScalingGroupsRoleName();
        String roleArn = "arn:aws:iam::" + asgAccount + ":role/" + roleName;

        AssumeRoleResult assumeRoleResult = sts.assumeRole(new AssumeRoleRequest()
                        .withRoleArn(roleArn)
                        .withRoleSessionName("sts-session-" + asgAccount)
        );

        return assumeRoleResult.getCredentials();
    }

    private AutoScalingGroup retrieveAutoScalingGroupCrossAccount(String asgAccount, String asgName) {
        logger.debug("Getting cross account ASG for asgName: {}, asgAccount: {}", asgName, asgAccount);

        Credentials credentials = stsCredentials.get(asgAccount);

        if (credentials == null || credentials.getExpiration().getTime() < System.currentTimeMillis() + 1000) {
            stsCredentials.put(asgAccount, initializeStsSession(asgAccount));
            credentials = stsCredentials.get(asgAccount);
        }

        ClientConfiguration clientConfiguration = new ClientConfiguration()
                .withConnectionTimeout(serverConfig.getASGQueryTimeoutMs());

        AmazonAutoScaling autoScalingClient = new AmazonAutoScalingClient(
                new BasicSessionCredentials(
                        credentials.getAccessKeyId(),
                        credentials.getSecretAccessKey(),
                        credentials.getSessionToken()
                ),
                clientConfiguration
        );

        String region = clientConfig.getRegion();
        if (!region.equals("us-east-1")) {
            autoScalingClient.setEndpoint("autoscaling." + region + ".amazonaws.com");
        }

        DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest()
                .withAutoScalingGroupNames(asgName);
        DescribeAutoScalingGroupsResult result = autoScalingClient.describeAutoScalingGroups(request);
        List<AutoScalingGroup> asgs = result.getAutoScalingGroups();
        if (asgs.isEmpty()) {
            return null;
        } else {
            return asgs.get(0);
        }
    }

    /**
     * Queries AWS to see if the load balancer flag is suspended.
     *
     * @param asgAccountid the accountId this asg resides in, if applicable (null will use the default accountId)
     * @param asgName the name of the asg
     * @return true, if the load balancer flag is not suspended, false otherwise.
     */
    private Boolean isASGEnabledinAWS(String asgAccountid, String asgName) {
        try {
            Stopwatch t = this.loadASGInfoTimer.start();
            boolean returnValue = !isAddToLoadBalancerSuspended(asgAccountid, asgName);
            t.stop();
            return returnValue;
        } catch (Throwable e) {
            logger.error("Could not get ASG information from AWS: ", e);
        }
        return Boolean.TRUE;
    }

    /**
     * Gets the number of elements in the ASG cache.
     *
     * @return the long value representing the number of elements in the ASG
     *         cache.
     */
    @com.netflix.servo.annotations.Monitor(name = "numOfElementsinASGCache",
            description = "Number of elements in the ASG Cache", type = DataSourceType.GAUGE)
    public long getNumberofElementsinASGCache() {
        return asgCache.size();
    }

    /**
     * Gets the number of ASG queries done in the period.
     *
     * @return the long value representing the number of ASG queries done in the
     *         period.
     */
    @com.netflix.servo.annotations.Monitor(name = "numOfASGQueries",
            description = "Number of queries made to AWS to retrieve ASG information", type = DataSourceType.COUNTER)
    public long getNumberofASGQueries() {
        return asgCache.stats().loadCount();
    }

    /**
     * Gets the number of ASG queries that failed because of some reason.
     *
     * @return the long value representing the number of ASG queries that failed
     *         because of some reason.
     */
    @com.netflix.servo.annotations.Monitor(name = "numOfASGQueryFailures",
            description = "Number of queries made to AWS to retrieve ASG information and that failed",
            type = DataSourceType.COUNTER)
    public long getNumberofASGQueryFailures() {
        return asgCache.stats().loadExceptionCount();
    }

    /**
     * Gets the task that updates the ASG information periodically.
     *
     * @return TimerTask that updates the ASG information periodically.
     */
    private TimerTask getASGUpdateTask() {
        return new TimerTask() {

            @Override
            public void run() {
                try {
                    // First get the active ASG names
                    Set<CacheKey> cacheKeys = getCacheKeys();
                    if (logger.isDebugEnabled()) {
                        logger.debug("Trying to  refresh the keys for {}", Arrays.toString(cacheKeys.toArray()));
                    }
                    for (CacheKey key : cacheKeys) {
                        try {
                            asgCache.refresh(key);
                        } catch (Throwable e) {
                            logger.error("Error updating the ASG cache for {}", key, e);
                        }

                    }

                } catch (Throwable e) {
                    logger.error("Error updating the ASG cache", e);
                }

            }

        };
    }

    /**
     * Get the cacheKeys of all the ASG to which query AWS for.
     *
     * <p>
     * The names are obtained from the {@link com.netflix.eureka.registry.InstanceRegistry} which is then
     * used for querying the AWS.
     * </p>
     *
     * @return the set of ASG cacheKeys (asgName + accountId).
     */
    private Set<CacheKey> getCacheKeys() {
        Set<CacheKey> cacheKeys = new HashSet<CacheKey>();
        Applications apps = registry.getApplicationsFromLocalRegionOnly();
        for (Application app : apps.getRegisteredApplications()) {
            for (InstanceInfo instanceInfo : app.getInstances()) {
                String localAccountId = getAccountId(instanceInfo, accountId);
                String asgName = instanceInfo.getASGName();
                if (asgName != null) {
                    CacheKey key = new CacheKey(localAccountId, asgName);
                    cacheKeys.add(key);
                }
            }
        }

        return cacheKeys;
    }

    /**
     * Get the AWS account id where an ASG is created.
     * Warning: This is expensive as it loops through all instances currently registered.
     *
     * @param asgName The name of the ASG
     * @return the account id
     */
    private String getASGAccount(String asgName) {
        Applications apps = registry.getApplicationsFromLocalRegionOnly();

        for (Application app : apps.getRegisteredApplications()) {
            for (InstanceInfo instanceInfo : app.getInstances()) {
                String thisAsgName = instanceInfo.getASGName();
                if (thisAsgName != null && thisAsgName.equals(asgName)) {
                    String localAccountId = getAccountId(instanceInfo, null);
                    if (localAccountId != null) {
                        return localAccountId;
                    }
                }
            }
        }

        logger.info("Couldn't get the ASG account for {}, using the default accountId instead", asgName);
        return accountId;
    }

    private String getAccountId(InstanceInfo instanceInfo, String fallbackId) {
        String localAccountId = null;

        DataCenterInfo dataCenterInfo = instanceInfo.getDataCenterInfo();
        if (dataCenterInfo instanceof AmazonInfo) {
            localAccountId = ((AmazonInfo) dataCenterInfo).get(MetaDataKey.accountId);
        }

        return localAccountId == null ? fallbackId : localAccountId;
    }

    private AmazonAutoScaling getAmazonAutoScalingClient() {
        String aWSAccessId = serverConfig.getAWSAccessId();
        String aWSSecretKey = serverConfig.getAWSSecretKey();
        ClientConfiguration clientConfiguration = new ClientConfiguration()
                .withConnectionTimeout(serverConfig.getASGQueryTimeoutMs());

        if (null != aWSAccessId && !"".equals(aWSAccessId) && null != aWSSecretKey && !"".equals(aWSSecretKey)) {
            return new AmazonAutoScalingClient(
                    new BasicAWSCredentials(aWSAccessId, aWSSecretKey),
                    clientConfiguration);
        } else {
            return new AmazonAutoScalingClient(
                    new InstanceProfileCredentialsProvider(),
                    clientConfiguration);
        }
    }

    private static String getAccountId() {
        InstanceInfo myInfo = ApplicationInfoManager.getInstance().getInfo();
        return ((AmazonInfo) myInfo.getDataCenterInfo()).get(MetaDataKey.accountId);
    }

    private static class CacheKey {
        final String asgAccountId;
        final String asgName;

        CacheKey(String asgAccountId, String asgName) {
            this.asgAccountId = asgAccountId;
            this.asgName = asgName;
        }

        @Override
        public String toString() {
            return "CacheKey{" +
                    "asgName='" + asgName + '\'' +
                    ", asgAccountId='" + asgAccountId + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheKey)) return false;

            CacheKey cacheKey = (CacheKey) o;

            if (asgAccountId != null ? !asgAccountId.equals(cacheKey.asgAccountId) : cacheKey.asgAccountId != null)
                return false;
            if (asgName != null ? !asgName.equals(cacheKey.asgName) : cacheKey.asgName != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = asgName != null ? asgName.hashCode() : 0;
            result = 31 * result + (asgAccountId != null ? asgAccountId.hashCode() : 0);
            return result;
        }
    }
}
