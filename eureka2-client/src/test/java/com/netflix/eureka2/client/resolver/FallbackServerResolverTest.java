package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.model.Server;
import org.junit.Test;
import rx.Observable;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author David Liu
 */
public class FallbackServerResolverTest extends AbstractResolverTest {

    @Test(timeout = 60000)
    public void testFailoverToSecondResolver() throws Exception {
        Server serverA = new Server("hostA", 123);

        ServerResolver goodResolver = ServerResolvers.from(serverA);
        ServerResolver brokenResolver = new ServerResolver() {
            @Override
            public void close() {
            }

            @Override
            public Observable<Server> resolve() {
                return Observable.error(new Exception("resolver error"));
            }
        };

        // First resolver ok
        ServerResolver resolver = ServerResolvers.fallbackResolver(goodResolver, brokenResolver);
        assertThat(takeNext(resolver), is(equalTo(serverA)));

        // First resolver broken
        resolver = ServerResolvers.fallbackResolver(brokenResolver, goodResolver);
        assertThat(takeNext(resolver), is(equalTo(serverA)));
    }
}
