/*
 * This class is intended to replace AwsAsgUtil in the next major version of eureka-core.
 * For now the code is split allowing optional use of aws jdk version 2
 */


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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.SpectatorUtil;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.registry.InstanceRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.AutoScalingClientBuilder;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import software.amazon.awssdk.services.autoscaling.model.DescribeAutoScalingGroupsResponse;
import software.amazon.awssdk.services.autoscaling.model.SuspendedProcess;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * A utility class for querying and updating information about amazon
 * autoscaling groups using the AWS APIs.
 *
 * @author Karthik Ranganathan
 *
 */
@Singleton
public class AwsAsgUtilV2 implements AsgClient {
    private static final Logger logger = LoggerFactory.getLogger(AwsAsgUtil.class);

    private static final String PROP_ADD_TO_LOAD_BALANCER = "AddToLoadBalancer";

    private static final String accountId = getAccountId();

    private Map<String, Credentials> stsCredentials = new HashMap<>();

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
    private final com.netflix.spectator.api.Timer loadASGInfoTimer = SpectatorUtil.timer("Eureka-loadASGInfo", AwsAsgUtil.class);

    private final EurekaServerConfig serverConfig;
    private final EurekaClientConfig clientConfig;
    private final InstanceRegistry registry;
    private final LoadingCache<CacheKey, Boolean> asgCache;
    private final AutoScalingClient awsClient;

    @Inject
    public AwsAsgUtilV2(EurekaServerConfig serverConfig,
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


        // Cache for the AWS ASG information
        Timer timer = new Timer("Eureka-ASGCacheRefresh", true);
        timer.schedule(getASGUpdateTask(),
                serverConfig.getASGUpdateIntervalMs(),
                serverConfig.getASGUpdateIntervalMs());
        SpectatorUtil.monitoredValue("numOfElementsinASGCache",
                this, AwsAsgUtilV2::getNumberofElementsinASGCache);
        SpectatorUtil.monitoredValue("numOfASGQueries",
                this, AwsAsgUtilV2::getNumberofASGQueries);
        SpectatorUtil.monitoredValue("numOfASGQueryFailures",
                this, AwsAsgUtilV2::getNumberofASGQueryFailures);
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
            if (!serverConfig.shouldUseAwsAsgApi()) {
                // Disabled, cached values (if any) are still being returned if the caller makes
                // a decision to call the disabled client during some sort of transitioning
                // period, but no new values will be fetched while disabled.

                logger.info(("'{}' is not cached at the moment and won't be fetched because querying AWS ASGs "
                                + "has been disabled via the config, returning the fallback value."),
                        cacheKey);

                return true;
            }

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
        List<SuspendedProcess> suspendedProcesses = asg.suspendedProcesses();
        for (SuspendedProcess process : suspendedProcesses) {
            if (PROP_ADD_TO_LOAD_BALANCER.equals(process.processName())) {
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
        if (asgName == null || asgName.isEmpty()) {
            logger.warn("null asgName specified, not attempting to retrieve AutoScalingGroup from AWS");
            return null;
        }
        // You can pass one name or a list of names in the request
        DescribeAutoScalingGroupsRequest request = DescribeAutoScalingGroupsRequest.builder()
                .autoScalingGroupNames(asgName)
                .build();
        DescribeAutoScalingGroupsResponse result = awsClient.describeAutoScalingGroups(request);
        List<AutoScalingGroup> asgs = result.autoScalingGroups();
        if (asgs.isEmpty()) {
            return null;
        } else {
            return asgs.get(0);
        }
    }

    private Credentials initializeStsSession(String asgAccount) {
        String region = clientConfig.getRegion();
        StsClientBuilder stsBuilder = StsClient.builder()
                .credentialsProvider(InstanceProfileCredentialsProvider.create())
                //setting the region based on config, letting jdk resolve endponit
                .region(Region.of(region));


        StsClient sts = stsBuilder.build();

        String roleName = serverConfig.getListAutoScalingGroupsRoleName();
        String roleArn = "arn:aws:iam::" + asgAccount + ":role/" + roleName;

        AssumeRoleRequest assumeRoleRequest = AssumeRoleRequest.builder()
                .roleArn(roleArn)
                .roleSessionName("sts-session-" + asgAccount)
                .build();

        AssumeRoleResponse assumeRoleResponse = sts.assumeRole(assumeRoleRequest);

        return assumeRoleResponse.credentials();
    }

    private AutoScalingGroup retrieveAutoScalingGroupCrossAccount(String asgAccount, String asgName) {
        logger.debug("Getting cross account ASG for asgName: {}, asgAccount: {}", asgName, asgAccount);

        Credentials credentials = stsCredentials.get(asgAccount);

        if (credentials == null || credentials.expiration().toEpochMilli() < System.currentTimeMillis() + 1000) {
            stsCredentials.put(asgAccount, initializeStsSession(asgAccount));
            credentials = stsCredentials.get(asgAccount);
        }

        AwsSessionCredentials awsSessionCredentials = AwsSessionCredentials.create(
                credentials.accessKeyId(),
                credentials.secretAccessKey(),
                credentials.sessionToken()
        );

        AutoScalingClientBuilder autoScalingClientBuilder = AutoScalingClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(awsSessionCredentials))
                //setting region based on config, letting sdk resolve endpoint
                .region(Region.of(clientConfig.getRegion()))
                .overrideConfiguration(
                        ClientOverrideConfiguration.builder()
                                .apiCallAttemptTimeout(Duration.ofMillis(serverConfig.getASGQueryTimeoutMs()))
                                .build());
        AutoScalingClient autoScalingClient = autoScalingClientBuilder.build();

        DescribeAutoScalingGroupsRequest request = DescribeAutoScalingGroupsRequest.builder()
                .autoScalingGroupNames(asgName)
                .build();
        DescribeAutoScalingGroupsResponse result = autoScalingClient.describeAutoScalingGroups(request);
        List<AutoScalingGroup> asgs = result.autoScalingGroups();
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
            final long t = SpectatorUtil.time(loadASGInfoTimer);
            boolean returnValue = !isAddToLoadBalancerSuspended(asgAccountid, asgName);
            SpectatorUtil.record(loadASGInfoTimer, t);
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
    public long getNumberofElementsinASGCache() {
        return asgCache.size();
    }

    /**
     * Gets the number of ASG queries done in the period.
     *
     * @return the long value representing the number of ASG queries done in the
     *         period.
     */
    public long getNumberofASGQueries() {
        return asgCache.stats().loadCount();
    }

    /**
     * Gets the number of ASG queries that failed because of some reason.
     *
     * @return the long value representing the number of ASG queries that failed
     *         because of some reason.
     */
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
                    if (!serverConfig.shouldUseAwsAsgApi()) {
                        // Disabled via the config, no-op.
                        return;
                    }

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
        Set<CacheKey> cacheKeys = new HashSet<>();
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

    private AutoScalingClient getAmazonAutoScalingClient() {
        String awsAccessId = serverConfig.getAWSAccessId();
        String awsSecretKey = serverConfig.getAWSSecretKey();


        AutoScalingClientBuilder builder = AutoScalingClient.builder()
                .overrideConfiguration(
                        ClientOverrideConfiguration.builder()
                                .apiCallAttemptTimeout(Duration.ofMillis(serverConfig.getASGQueryTimeoutMs()))
                                .build())
                        .region(Region.of(clientConfig.getRegion()));

        if (awsAccessId != null && !awsAccessId.isEmpty() && awsSecretKey != null && !awsSecretKey.isEmpty()) {
            AwsBasicCredentials awsBasicCredentials = AwsBasicCredentials.create(awsAccessId, awsSecretKey);
            builder.credentialsProvider(StaticCredentialsProvider.create(awsBasicCredentials));
        } else {
            builder.credentialsProvider(InstanceProfileCredentialsProvider.create());
        }

        return builder.build();
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
