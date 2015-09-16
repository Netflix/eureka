package com.netflix.eureka;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka.util.EIPManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;

public class EIPEurekaBinder implements EurakaBinder {

    private static final Logger logger = LoggerFactory.getLogger(EIPEurekaBinder.class);
    private static final Timer timer = new Timer("Eureka-EIPBinder", true);
    private static final int EIP_BIND_SLEEP_TIME_MS = 1000;

    @Override
    public void unbind() throws InterruptedException {

        InstanceInfo info = ApplicationInfoManager.getInstance().getInfo();

        for (int i = 0; i < EurekaServerConfigurationManager.getInstance()
                .getConfiguration().getEIPBindRebindRetries(); i++) {
            try {
                if (DataCenterInfo.Name.Amazon.equals(info.getDataCenterInfo().getName())) {
                    EIPManager.getInstance().unbindEIP();
                }
                break;
            } catch (Throwable e) {
                logger.warn("Cannot unbind the EIP from the instance");
                Thread.sleep(1000);
            }
        }
    }

    @Override
    public void bind() throws InterruptedException {
        PeerAwareInstanceRegistryImpl registry = PeerAwareInstanceRegistryImpl.getInstance();
        InstanceInfo info = ApplicationInfoManager.getInstance().getInfo();

        // Only in AWS, enable the binding functionality
        if (DataCenterInfo.Name.Amazon.equals(info.getDataCenterInfo().getName())) {
            handleEIPBinding(registry);
        }

    }

    /**
     * Handles EIP binding process in AWS Cloud.
     *
     * @throws InterruptedException
     */
    private void handleEIPBinding(PeerAwareInstanceRegistryImpl registry)
            throws InterruptedException {
        EurekaServerConfig eurekaServerConfig = EurekaServerConfigurationManager.getInstance().getConfiguration();
        int retries = eurekaServerConfig.getEIPBindRebindRetries();
        // Bind to EIP if needed
        EIPManager eipManager = EIPManager.getInstance();
        for (int i = 0; i < retries; i++) {
            try {
                if (eipManager.isEIPBound()) {
                    break;
                } else {
                    eipManager.bindEIP();
                }
            } catch (Throwable e) {
                logger.error("Cannot bind to EIP", e);
                Thread.sleep(EIP_BIND_SLEEP_TIME_MS);
            }
        }
        // Schedule a timer which periodically checks for EIP binding.
        scheduleEIPBindTask(eurekaServerConfig, registry);
    }

    /**
     * Schedules a EIP binding timer task which constantly polls for EIP in the
     * same zone and binds it to itself.If the EIP is taken away for some
     * reason, this task tries to get the EIP back. Hence it is advised to take
     * one EIP assignment per instance in a zone.
     *
     * @param eurekaServerConfig
     *            the Eureka Server Configuration.
     */
    private void scheduleEIPBindTask(
            EurekaServerConfig eurekaServerConfig, final PeerAwareInstanceRegistryImpl registry) {
        timer.schedule(new TimerTask() {

                           @Override
                           public void run() {
                               try {
                                   // If the EIP is not bound, the registry could  be stale
                                   // First sync up the registry from the neighboring node before
                                   // trying to bind the EIP
                                   EIPManager eipManager = EIPManager.getInstance();
                                   if (!eipManager.isEIPBound()) {
                                       registry.clearRegistry();
                                       int count = registry.syncUp();
                                       registry.openForTraffic(count);
                                   } else {
                                       // An EIP is already bound
                                       return;
                                   }
                                   eipManager.bindEIP();
                               } catch (Throwable e) {
                                   logger.error("Could not bind to EIP", e);
                               }
                           }
                       }, eurekaServerConfig.getEIPBindingRetryIntervalMs(),
                eurekaServerConfig.getEIPBindingRetryIntervalMs());
    }

}
