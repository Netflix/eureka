package com.netflix.eureka2.server.service.bootstrap;

import com.netflix.eureka2.health.HealthStatusUpdate;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Source.Origin;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Observable;

import static com.netflix.eureka2.server.config.bean.WriteServerConfigBean.aWriteServerConfig;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class RegistryBootstrapCoordinatorTest {

    private static final InstanceInfo INSTANCE = SampleInstanceInfo.WebServer.build();
    private static final Source SOURCE = new Source(Origin.BOOTSTRAP, "test");

    private final RegistryBootstrapService bootstrapService = mock(RegistryBootstrapService.class);
    private final SourcedEurekaRegistry<InstanceInfo> registry = new SourcedEurekaRegistryImpl(EurekaRegistryMetricFactory.registryMetrics());

    private RegistryBootstrapCoordinator bootstrapCoordinator;

    @Before
    public void setUp() throws Exception {
        when(bootstrapService.loadIntoRegistry(any(SourcedEurekaRegistry.class), any(Source.class))).thenAnswer(new Answer<Observable<Void>>() {
            @Override
            public Observable<Void> answer(InvocationOnMock invocation) throws Throwable {
                registry.register(INSTANCE, SOURCE).subscribe();
                return Observable.empty();
            }
        });

        WriteServerConfig config = aWriteServerConfig().withBootstrapEnabled(true).build();
        bootstrapCoordinator = new RegistryBootstrapCoordinator(config, bootstrapService, registry);
    }

    @Test
    public void testRegistryBootstrap() throws Exception {
        bootstrapCoordinator.bootstrap();

        // If bootstrap is, health status should change to UP
        ExtTestSubscriber<HealthStatusUpdate<?>> testSubscriber = new ExtTestSubscriber<>();
        bootstrapCoordinator.healthStatus().subscribe(testSubscriber);

        assertThat(testSubscriber.takeNextOrWait().getStatus(), is(equalTo(Status.UP)));
    }
}