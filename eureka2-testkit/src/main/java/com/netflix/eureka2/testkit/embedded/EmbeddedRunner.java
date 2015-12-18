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

    public EmbeddedRunner(int writeSize,
                          int readSize,
                          boolean withDashboard,
                          boolean withExt,
                          boolean withAdminUI,
                          boolean withDeploymentView,
                          boolean withEphemeralPorts) {
        deployment = new EurekaDeploymentBuilder()
                .withWriteClusterSize(writeSize)
                .withReadClusterSize(readSize)
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

        boolean withDashboard = false;
        if (args.length >= 3) {
            withDashboard = Boolean.valueOf(args[2]);
        }
        boolean withDeploymentView = false;
        if (args.length >= 4) {
            withDeploymentView = Boolean.valueOf(args[3]);
        }
        boolean withExt = false;
        if (args.length >= 5) {
            withExt = Boolean.valueOf(args[4]);
        }
        boolean witAdminUI = false;
        if (args.length >= 6) {
            witAdminUI = Boolean.valueOf(args[5]);
        }
        boolean withEphemeralPorts = false;
        if (args.length >= 7) {
            withEphemeralPorts = Boolean.valueOf(args[6]);
        }

        EmbeddedRunner runner = new EmbeddedRunner(writeCount, readCount, withDashboard, withExt, witAdminUI, withDeploymentView, withEphemeralPorts);
        runner.waitTillShutdown();
    }
}
