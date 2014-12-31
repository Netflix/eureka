package com.netflix.eureka2.testkit.embedded;

import java.util.concurrent.CountDownLatch;

import com.netflix.eureka2.testkit.embedded.EurekaDeployment.EurekaDeploymentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public class EmbeddedRunner {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedRunner.class);

    private final EurekaDeployment deployment;

    public EmbeddedRunner(int writeSize, int readSize) {
        this(writeSize, readSize, false, false, false, false, false, false);
    }

    public EmbeddedRunner(int writeSize,
                          int readSize,
                          boolean withBridge,
                          boolean withDashboard,
                          boolean withExt,
                          boolean withAdminUI,
                          boolean withDeploymentView,
                          boolean withEphemeralPorts) {
        deployment = new EurekaDeploymentBuilder()
                .withWriteClusterSize(writeSize)
                .withReadClusterSize(readSize)
                .withBridge(withBridge)
                .withDashboard(withDashboard)
                .withExtensions(withExt)
                .withAdminUI(withAdminUI)
                .withDeploymentView(withDeploymentView)
                .withEphemeralPorts(withEphemeralPorts)
                .build();
        logger.info("Eureka clusters are up");
    }

    public void waitTillShutdown() {
        final CountDownLatch shutdownFinished = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    shutdown();
                    logger.info("Leaving main loop - shutdown finished.");
                } finally {
                    shutdownFinished.countDown();
                }
            }
        });
        while (true) {
            try {
                shutdownFinished.await();
                return;
            } catch (InterruptedException e) {
                // IGNORE
            }
        }
    }

    public void shutdown() {
        deployment.shutdown();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("ERROR: required number of write and read servers");
            System.exit(-1);
        }
        int writeCount = Integer.valueOf(args[0]);
        int readCount = Integer.valueOf(args[1]);

        boolean withBridge = false;
        if (args.length >= 3) {
            withBridge = Boolean.valueOf(args[2]);
        }
        boolean withDashboard = false;
        if (args.length >= 4) {
            withDashboard = Boolean.valueOf(args[3]);
        }
        boolean withDeploymentView = false;
        if (args.length >= 5) {
            withDeploymentView = Boolean.valueOf(args[4]);
        }
        boolean withExt = false;
        if (args.length >= 6) {
            withExt = Boolean.valueOf(args[5]);
        }
        boolean witAdminUI = false;
        if (args.length >= 7) {
            witAdminUI = Boolean.valueOf(args[6]);
        }
        boolean withEphemeralPorts = false;
        if (args.length >= 8) {
            withEphemeralPorts = Boolean.valueOf(args[7]);
        }

        new EmbeddedRunner(writeCount, readCount, withBridge, withDashboard, withExt, witAdminUI, withDeploymentView, withEphemeralPorts).waitTillShutdown();
    }

}
