package com.netflix.eureka.aws;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Timer;
import java.util.TimerTask;

import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.PeerAwareInstanceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractEipManager implements EIPManager {

    private static final Logger logger = LoggerFactory.getLogger(AbstractEipManager.class);

    private static final int EIP_BIND_SLEEP_TIME_MS = 1000;

    private final Timer timer = new Timer("Eureka-EIPBinder", true);

    protected final EurekaServerConfig eurekaServerConfig;
    protected final PeerAwareInstanceRegistry registry;

    protected AbstractEipManager(EurekaServerConfig eurekaServerConfig, PeerAwareInstanceRegistry registry) {
        this.eurekaServerConfig = eurekaServerConfig;
        this.registry = registry;
    }

    @PostConstruct
    @Override
    public void start() {
        int retries = eurekaServerConfig.getEIPBindRebindRetries();
        for (int i = 0; i < retries; i++) {
            try {
                if (isEIPBound()) {
                    break;
                } else {
                    bindEIP();
                }
            } catch (Throwable e) {
                logger.error("Cannot bind to EIP", e);
                try {
                    Thread.sleep(EIP_BIND_SLEEP_TIME_MS);
                } catch (InterruptedException ignored) {
                    logger.warn("EIP binding process interrupted");
                    return;
                }
            }
        }
        scheduleEIPBindTask();
    }

    @PreDestroy
    @Override
    public void stop() {
        timer.cancel();
        unbindEIP();
    }

    /**
     * Schedules a EIP binding timer task which constantly polls for EIP in the
     * same zone and binds it to itself.If the EIP is taken away for some
     * reason, this task tries to get the EIP back. Hence it is advised to take
     * one EIP assignment per instance in a zone.
     */
    private void scheduleEIPBindTask() {
        timer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            // If the EIP is not bound, the registry could  be stale
                            // First sync up the registry from the neighboring node before
                            // trying to bind the EIP
                            if (!isEIPBound()) {
                                registry.clearRegistry();
                                int count = registry.syncUp();
                                registry.openForTraffic(count);
                            } else {
                                // An EIP is already bound
                                return;
                            }
                            bindEIP();
                        } catch (Throwable e) {
                            logger.error("Could not bind to EIP", e);
                        }
                    }
                },
                eurekaServerConfig.getEIPBindingRetryIntervalMs(),
                eurekaServerConfig.getEIPBindingRetryIntervalMs());
    }
}
