package com.netflix.eureka2.integration.server.startup;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.server.EurekaServerRunner;
import com.netflix.eureka2.server.resolver.ClusterAddress;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import com.netflix.eureka2.utils.Json;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ObservableConnection;
import org.codehaus.jackson.JsonNode;
import org.junit.Before;
import org.junit.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;

import static com.netflix.eureka2.interests.ChangeNotifications.dataOnlyFilter;
import static com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource.anEurekaDeploymentResource;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractStartupAndShutdownIntegrationTest<RUNNER extends EurekaServerRunner<?>> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractStartupAndShutdownIntegrationTest.class);

    private static final String SHUTDOWN_CMD = "shutdown\n";

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = anEurekaDeploymentResource(1, 0).build();

    protected String writeServerList;
    protected ClusterAddress[] clusterAddresses;

    @Before
    public void setUp() throws Exception {
        EmbeddedWriteServer server = eurekaDeploymentResource.getEurekaDeployment().getWriteCluster().getServer(0);
        writeServerList = "localhost:" + server.getRegistrationPort() + ':' + server.getInterestPort() + ':' + server.getReplicationPort();
        clusterAddresses = new ClusterAddress[]{ClusterAddress.valueOf(writeServerList)};
    }

    protected void verifyThatStartsWithFileBasedConfiguration(String serverName, RUNNER server) throws Exception {
        injectConfigurationValuesViaSystemProperties(serverName);
        try {
            executeAndVerifyLifecycle(server, serverName);
        } finally {
            removeConfigurationValuesViaSystemProperties();
        }
    }

    protected void injectConfigurationValuesViaSystemProperties(String appName) {
        // These properties are resolved in {write|read|dashboard|bridge}-server-startupAndShutdown.properties, and
        // write-server-startupAndShutdown.properties file.
        System.setProperty("eureka.test.startupAndShutdown.serverList", writeServerList);
        System.setProperty("eureka.test.startupAndShutdown.appName", appName);
    }

    protected void removeConfigurationValuesViaSystemProperties() {
        System.clearProperty("eureka.test.startupAndShutdown.serverList");
        System.clearProperty("eureka.test.startupAndShutdown.appName");
    }

    protected void executeAndVerifyLifecycle(RUNNER serverRunner, String applicationName) throws Exception {
        serverRunner.start();

        // Verify that server health status is up
        verifyHealthStatusIsUp(serverRunner);

        // Subscribe to write cluster and verify that new server connected properly
        EurekaInterestClient interestClient = eurekaDeploymentResource.interestClientToWriteCluster();

        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        interestClient.forInterest(Interests.forApplications(applicationName)).filter(dataOnlyFilter()).subscribe(testSubscriber);

        ChangeNotification<InstanceInfo> notification = testSubscriber.takeNextOrWait();
        assertThat(notification.getKind(), is(equalTo(Kind.Add)));

        // Shutdown new server
        sendShutdownCommand(serverRunner.getEurekaServer().getShutdownPort());
        serverRunner.awaitTermination();

        // Verify that read server registry entry is removed
        notification = testSubscriber.takeNextOrWait();
        assertThat(notification.getKind(), is(equalTo(Kind.Delete)));
    }

    private void verifyHealthStatusIsUp(RUNNER serverRunner) {
        int httpPort = serverRunner.getEurekaServer().getHttpServerPort();
        RxNetty.newWebSocketClientBuilder("localhost", httpPort).withWebSocketURI("/health").build()
                .connect()
                .flatMap(new Func1<ObservableConnection<WebSocketFrame, WebSocketFrame>, Observable<Status>>() {
                    @Override
                    public Observable<Status> call(ObservableConnection<WebSocketFrame, WebSocketFrame> connection) {
                        return connection.getInput().flatMap(new Func1<WebSocketFrame, Observable<Status>>() {
                            @Override
                            public Observable<Status> call(WebSocketFrame webSocketFrame) {
                                String body = ((TextWebSocketFrame) webSocketFrame).text();
                                try {
                                    JsonNode jsonNode = Json.getMapper().readTree(body);
                                    Status status = Status.toEnum(jsonNode.get("status").asText());
                                    return Observable.just(status);
                                } catch (IOException e) {
                                    return Observable.error(e);
                                }
                            }
                        });
                    }
                })
                .filter(new Func1<Status, Boolean>() {
                    @Override
                    public Boolean call(Status status) {
                        return status == Status.UP;
                    }
                })
                .take(1)
                .timeout(30, TimeUnit.SECONDS)
                .toBlocking()
                .first();
    }

    protected void sendShutdownCommand(final int port) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                RxNetty.createTcpClient("localhost", port).connect().flatMap(new Func1<ObservableConnection<ByteBuf, ByteBuf>, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(final ObservableConnection<ByteBuf, ByteBuf> connection) {
                        return connection.writeStringAndFlush(SHUTDOWN_CMD)
                                .finallyDo(new Action0() {
                                    @Override
                                    public void call() {
                                        connection.close().subscribe();
                                    }
                                });
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
