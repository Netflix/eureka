package com.netflix.eureka2.client.resolver;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.resolver.ServerResolver.Server;
import com.netflix.eureka2.rx.RxBlocking;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractResolverTest {

    protected static final Server SERVER_A = new Server("hostA", 123);
    protected static final Server SERVER_B = new Server("hostB", 124);

    protected Server takeNext(ServerResolver resolver) {
        Iterator<Server> serverIterator = RxBlocking.iteratorFrom(30, TimeUnit.SECONDS, resolver.resolve());
        Server next = serverIterator.next();
        assertThat(serverIterator.hasNext(), is(false));
        return next;
    }
}
