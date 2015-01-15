package com.netflix.eureka2.client.resolver;

import java.util.Set;

import com.netflix.eureka2.client.resolver.ServerResolver.Server;
import org.junit.Test;

import static com.netflix.eureka2.utils.Sets.asSet;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class StaticServerResolverTest extends AbstractResolverTest {
    @Test
    public void testResolvesFromServerList() throws Exception {
        ServerResolver resolver = ServerResolvers.from(SERVER_A, SERVER_B);

        Set<Server> expected = asSet(SERVER_A, SERVER_B);
        Set<Server> actual = asSet(takeNext(resolver), takeNext(resolver));

        assertThat(expected, is(equalTo(actual)));
    }
}