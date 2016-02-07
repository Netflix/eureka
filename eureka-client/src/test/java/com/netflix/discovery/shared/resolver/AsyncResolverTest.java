package com.netflix.discovery.shared.resolver;

import com.netflix.discovery.shared.resolver.aws.SampleCluster;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class AsyncResolverTest {

    private final EurekaTransportConfig transportConfig = mock(EurekaTransportConfig.class);
    private final ClusterResolver delegateResolver = mock(ClosableResolver.class);

    private AsyncResolver resolver;

    @Before
    public void setUp() {
        when(transportConfig.getAsyncExecutorThreadPoolSize()).thenReturn(3);
        when(transportConfig.getAsyncResolverRefreshIntervalMs()).thenReturn(200);
        when(transportConfig.getAsyncResolverWarmUpTimeoutMs()).thenReturn(100);

        resolver = spy(new AsyncResolver(
                "test",
                delegateResolver,
                transportConfig.getAsyncExecutorThreadPoolSize(),
                transportConfig.getAsyncResolverRefreshIntervalMs(),
                transportConfig.getAsyncResolverWarmUpTimeoutMs()
        ));
    }

    @After
    public void shutDown() {
        resolver.shutdown();
    }

    @Test
    public void testHappyCase() {
        List delegateReturns1 = new ArrayList(SampleCluster.UsEast1a.builder().withServerPool(2).build());
        List delegateReturns2 = new ArrayList(SampleCluster.UsEast1b.builder().withServerPool(3).build());

        when(delegateResolver.getClusterEndpoints())
                .thenReturn(delegateReturns1)
                .thenReturn(delegateReturns2);

        List endpoints = resolver.getClusterEndpoints();
        assertThat(endpoints.size(), equalTo(delegateReturns1.size()));
        verify(delegateResolver, times(1)).getClusterEndpoints();
        verify(resolver, times(1)).doWarmUp();

        // try again, should be async
        endpoints = resolver.getClusterEndpoints();
        assertThat(endpoints.size(), equalTo(delegateReturns1.size()));
        verify(delegateResolver, times(1)).getClusterEndpoints();
        verify(resolver, times(1)).doWarmUp();

        // wait for the next async update cycle
        verify(delegateResolver, timeout(1000).times(2)).getClusterEndpoints();
        endpoints = resolver.getClusterEndpoints();
        assertThat(endpoints.size(), equalTo(delegateReturns2.size()));
        verify(delegateResolver, times(2)).getClusterEndpoints();
        verify(resolver, times(1)).doWarmUp();
    }

    @Test
    public void testDelegateFailureAtWarmUp() {
        when(delegateResolver.getClusterEndpoints())
                .thenReturn(null);

        // override the scheduling which will be triggered immediately if warmUp fails (as is intended).
        // do this to avoid thread race conditions for a more predictable test
        doNothing().when(resolver).scheduleTask(anyLong());

        List endpoints = resolver.getClusterEndpoints();
        assertThat(endpoints.isEmpty(), is(true));
        verify(delegateResolver, times(1)).getClusterEndpoints();
        verify(resolver, times(1)).doWarmUp();
    }
}
