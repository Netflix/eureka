package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.utils.Server;
import org.junit.Test;
import rx.Observable;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class ServerResolverFailoverChainTest extends AbstractResolverTest {

    @Test(timeout = 60000)
    public void testFailoverToSecondResolver() throws Exception {
        Server serverA = new Server("hostA", 123);

        ServerResolver goodResolver = ServerResolvers.from(serverA);
        ServerResolver brokenResolver = new ServerResolver() {
            @Override
            public Observable<Server> resolve() {
                return Observable.error(new Exception("resolver error"));
            }

            @Override
            public void close() {
            }
        };

        // First resolver ok
        ServerResolver resolver = ServerResolvers.failoverChainFrom(goodResolver, brokenResolver);
        assertThat(takeNext(resolver), is(equalTo(serverA)));

        // First resolver broken
        resolver = ServerResolvers.failoverChainFrom(brokenResolver, goodResolver);
        assertThat(takeNext(resolver), is(equalTo(serverA)));
    }
}