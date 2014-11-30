package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.client.resolver.ServerResolver.Server;
import com.netflix.eureka2.rx.RxBlocking;
import com.netflix.eureka2.utils.Sets;
import org.junit.Test;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * @author David Liu
 */
public class CompositeServerResolverTest {
    @Test
    public void testCompositeServerResolver() {
        Server[] expected = {new Server("ip1", 123), new Server("ip2", 123), new Server("ip3", 123), new Server("i42", 123)};

        ServerResolver resolver1 = ServerResolvers.from(expected[0], expected[1]);
        ServerResolver resolver2 = ServerResolvers.from(expected[2], expected[3]);
        ServerResolver compositeResolver = ServerResolvers.from(resolver1, resolver2);

        Set<Server> expectedSet = Sets.asSet(expected);

        Iterator<Server> serverIterator = RxBlocking.iteratorFrom(30, TimeUnit.SECONDS, compositeResolver.resolve());
        assertTrue(expectedSet.contains(serverIterator.next()));
        assertTrue(expectedSet.contains(serverIterator.next()));
    }
}
