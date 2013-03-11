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

package com.netflix.eureka.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.SuspendedProcess;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerConfigurationManager;
import com.netflix.eureka.InstanceRegistry;
import com.netflix.eureka.PeerAwareInstanceRegistry;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;

/**
 * A utility class for querying and updating information about amazon
 * autoscaling groups using the AWS APIs.
 * 
 * @author Karthik Ranganathan
 * 
 */

public class AwsAsgUtil {
    private final Logger logger = LoggerFactory.getLogger(AwsAsgUtil.class);
    private static final String PROP_ADD_TO_LOAD_BALANCER = "AddToLoadBalancer";
    private static final EurekaServerConfig eurekaConfig = EurekaServerConfigurationManager
            .getInstance().getConfiguration();
    private static final AmazonAutoScaling client = getAmazonAutoScalingClient();
    // Cache for the AWS ASG information
    private final LoadingCache<String, Boolean> asgCache = CacheBuilder
            .newBuilder().initialCapacity(500)
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build(new CacheLoader<String, Boolean>() {

                @Override
                public Boolean load(String key) throws Exception {
                    return isASGEnabledinAWS(key);
                }
            });

    private final Timer timer = new Timer("Eureka-ASGCacheRefresh", true);
    private final com.netflix.servo.monitor.Timer loadASGInfoTimer = Monitors
            .newTimer("Eureka-loadASGInfo");

    private static final AwsAsgUtil awsAsgUtil = new AwsAsgUtil();

    private AwsAsgUtil() {
        String region = DiscoveryManager.getInstance().getEurekaClientConfig()
                .getRegion();
        client.setEndpoint("autoscaling." + region + ".amazonaws.com");
        timer.schedule(getASGUpdateTask(),
                eurekaConfig.getASGUpdateIntervalMs(),
                eurekaConfig.getASGUpdateIntervalMs());

        try {

            Monitors.registerObject(this);

        } catch (Throwable e) {
            logger.warn("Cannot register the JMX monitor :", e);
        }
    }

    public static AwsAsgUtil getInstance() {
        return awsAsgUtil;
    }

    /**
     * Return the status of the ASG whether is enabled or disabled for service.
     * The value is picked up from the cache except the very first time.
     * 
     * @param asgName
     *            - The name of the ASG
     * @return - true if enabled, false otherwise
     */
    public boolean isASGEnabled(String asgName) {
        try {
            return asgCache.get(asgName);
        } catch (ExecutionException e) {
            logger.error("Error getting cache value for asg : " + asgName, e);
        }
        return true;
    }

    /**
     * Sets the status of the ASG
     * 
     * @param asgName
     *            - The name of the ASG
     * @param enabled
     *            - true to enable, false to disable
     */
    public void setStatus(String asgName, boolean enabled) {
        asgCache.put(asgName, enabled);
    }

    /**
     * Check if the ASG is disabled. The amazon flag "AddToLoadBalancer" is
     * queried to figure out if it is or not.
     * 
     * @param asgName
     *            - The name of the ASG for which the status needs to be queried
     * @return - true if the ASG is disabled, false otherwise
     */
    private boolean isAddToLoadBalancerSuspended(String asgName) {
        AutoScalingGroup asg = retrieveAutoScalingGroup(asgName);
        if (asg == null) {
            logger.warn(
                    "The ASG information for {} could not be found. So returning false.",
                    asgName);
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
        // You can pass one name or a list of names in the request
        DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest()
                .withAutoScalingGroupNames(asgName);
        DescribeAutoScalingGroupsResult result = client
                .describeAutoScalingGroups(request);
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
     * @param key
     *            - The name of the ASG for which the flag needs to be checked.
     * @return - true, if the load balancer flag is not suspended, false
     *         otherwise.
     */
    private Boolean isASGEnabledinAWS(Object key) {
        String myKey = (String) key;
        try {
            Stopwatch t = this.loadASGInfoTimer.start();
            boolean returnValue = !isAddToLoadBalancerSuspended(myKey);
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
    @com.netflix.servo.annotations.Monitor(name = "numOfElementsinASGCache", description = "Number of elements in the ASG Cache", type = DataSourceType.GAUGE)
    public long getNumberofElementsinASGCache() {
        return asgCache.size();
    }

    /**
     * Gets the number of ASG queries done in the period.
     * 
     * @return the long value representing the number of ASG queries done in the
     *         period.
     */
    @com.netflix.servo.annotations.Monitor(name = "numOfASGQueries", description = "Number of queries made to AWS to retrieve ASG information", type = DataSourceType.COUNTER)
    public long getNumberofASGQueries() {
        return asgCache.stats().loadCount();
    }

    /**
     * Gets the number of ASG queries that failed because of some reason.
     * 
     * @return the long value representing the number of ASG queries that failed
     *         because of some reason.
     */
    @com.netflix.servo.annotations.Monitor(name = "numOfASGQueryFailures", description = "Number of queries made to AWS to retrieve ASG information and that failed", type = DataSourceType.COUNTER)
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
                    Set<String> asgNames = getASGNames();
                    logger.debug("Trying to  refresh the keys for {}",
                            Arrays.toString(asgNames.toArray()));
                    for (String key : asgNames) {
                        try {
                            asgCache.refresh(key);
                        } catch (Throwable e) {
                            logger.error("Error updating the ASG cache for "
                                    + key, e);
                        }

                    }

                } catch (Throwable e) {
                    logger.error("Error updating the ASG cache", e);
                }

            }

        };
    }

    /**
     * Get the names of all the ASG to which query AWS for.
     * 
     * <p>
     * The names are obtained from the {@link InstanceRegistry} which is then
     * used for querying the AWS.
     * </p>
     * 
     * @return the set of ASG names.
     */
    private Set<String> getASGNames() {
        Set<String> asgNames = new HashSet<String>();
        Applications apps = PeerAwareInstanceRegistry.getInstance()
        .getApplications(false);
        for (Application app : apps.getRegisteredApplications()) {
            for (InstanceInfo instanceInfo : app.getInstances()) {
                String asgName = instanceInfo.getASGName();
                if (asgName != null) {
                    asgNames.add(asgName);
                }
            }
        }

        return asgNames;
    }

    private static AmazonAutoScaling getAmazonAutoScalingClient() {
        String aWSAccessId = eurekaConfig.getAWSAccessId();
        String aWSSecretKey = eurekaConfig.getAWSSecretKey();
        ClientConfiguration clientConfiguration = new ClientConfiguration()
                .withConnectionTimeout(eurekaConfig.getASGQueryTimeoutMs());

        if (null != aWSAccessId && !"".equals(aWSAccessId) &&
                null != aWSSecretKey && !"".equals(aWSSecretKey)) {
            return new AmazonAutoScalingClient(
                    new BasicAWSCredentials(aWSAccessId, aWSSecretKey),
                    clientConfiguration);
        }
        else
        {
            return new AmazonAutoScalingClient(
                    new InstanceProfileCredentialsProvider(),
                    clientConfiguration);
        }
    }
   
}
