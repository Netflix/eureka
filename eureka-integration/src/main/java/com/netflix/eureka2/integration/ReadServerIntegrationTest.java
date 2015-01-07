package com.netflix.eureka2.integration;

import com.netflix.eureka2.integration.categories.IntegrationTest;
import com.netflix.eureka2.server.EurekaReadServer;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ObservableConnection;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;

/**
 * @author David Liu
 */
@Category(IntegrationTest.class)
public class ReadServerIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(ReadServerIntegrationTest.class);

    private static final String SHUTDOWN_CMD = "shutdown\n";

    @Test
    public void testRemoteShutdownStopsAllServices() throws Exception {
        EurekaServerConfig config = new EurekaServerConfig.EurekaServerConfigBuilder().build();
        final EurekaReadServer server = new EurekaReadServer(config);
        server.start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                RxNetty.createTcpClient("localhost", 7700).connect().flatMap(new Func1<ObservableConnection<ByteBuf, ByteBuf>, Observable<Void>>() {
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

        server.waitTillShutdown();
    }
}
