package com.netflix.eureka2.integration.startup;

import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ObservableConnection;
import org.junit.Before;
import org.junit.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractStartupAndShutdownIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(AbstractStartupAndShutdownIntegrationTest.class);

    private static final String SHUTDOWN_CMD = "shutdown\n";

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = new EurekaDeploymentResource(1, 0);

    protected String[] writeServerList;

    @Before
    public void setUp() throws Exception {
        EmbeddedWriteServer server = eurekaDeploymentResource.getEurekaDeployment().getWriteCluster().getServer(0);
        writeServerList = new String[]{
                "localhost:" + server.getRegistrationPort() + ':' + server.getDiscoveryPort() + ':' + server.getReplicationPort()
        };
    }

    protected void injectConfigurationValuesViaSystemProperties(String appName) {
        // These properties are resolved in read-server-startupAndShutdown.properties, and
        // write-server-startupAndShutdown.properties file.
        System.setProperty("eureka.test.startupAndShutdown.serverList", writeServerList[0]);
        System.setProperty("eureka.test.startupAndShutdown.appName", appName);
    }

    protected void clearConfigurationValuesViaSystemProperties() {
        // These properties are resolved in read-server-startupAndShutdown.properties, and
        // write-server-startupAndShutdown.properties file.
        System.clearProperty("eureka.test.startupAndShutdown.serverList");
        System.clearProperty("eureka.test.startupAndShutdown.appName");
    }

    protected void sendShutdownCommand(final int port) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                RxNetty.createTcpClient("localhost", port).connect().flatMap(new Func1<ObservableConnection<ByteBuf, ByteBuf>, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(ObservableConnection<ByteBuf, ByteBuf> connection) {
                        connection.writeStringAndFlush(SHUTDOWN_CMD);
                        return connection.close();
                    }
                }).subscribe(
                        new Subscriber<Void>() {
                            @Override
                            public void onCompleted() {
                                logger.debug("Shutdown command send");
                            }

                            @Override
                            public void onError(Throwable e) {
                                logger.error("Failed to send shutdown command", e);
                            }

                            @Override
                            public void onNext(Void aVoid) {
                            }
                        }
                );
            }
        }).start();
    }
}
