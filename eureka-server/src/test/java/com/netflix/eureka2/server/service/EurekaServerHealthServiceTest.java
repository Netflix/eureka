package com.netflix.eureka2.server.service;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.ServicePort;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

/**
 * @author David Liu
 */
public class EurekaServerHealthServiceTest {

    private static final int TEST_HEALTHCHECK_INTERVAL_S = 60;

    private static final HashSet<ServicePort> PORTS = new HashSet<>();
    static {
        PORTS.add(new ServicePort(Names.DISCOVERY, 11111, false));
        PORTS.add(new ServicePort(Names.REGISTRATION, 22222, true));
    }

    private TestScheduler testScheduler;
    private TestEurekaServerHealthService testService;

    private CountDownLatch initHealthCheckLatch;
    private AtomicBoolean initHealthCheck;

    @Before
    public void setUp() {
        initHealthCheck = new AtomicBoolean(true);
        testScheduler = Schedulers.test();
        testService = spy(new TestEurekaServerHealthService(testScheduler));
    }

    @After
    public void tearDown() {
        testService.shutdown();
    }

    @Test
    public void testInitialRegistrationWithStartingStatus() throws Exception {
        // don't init the health check
        initHealthCheck.set(false);

        initHealthCheckLatch = new CountDownLatch(1);
        testService.init();

        assertThat(initHealthCheckLatch.await(30, TimeUnit.SECONDS), equalTo(true));

        ArgumentCaptor<InstanceInfo> argument = ArgumentCaptor.forClass(InstanceInfo.class);
        verify(testService, times(1)).report(argument.capture());

        InstanceInfo captured = argument.getValue();
        assertThat(captured.getStatus(), equalTo(InstanceInfo.Status.STARTING));
        assertThat(captured.getPorts(), containsInAnyOrder(PORTS.toArray()));
    }

    @Test
    public void testResolveServerHealthDuplicates() throws Exception {

        testService.initHealthCheck();
        testScheduler.advanceTimeBy(TEST_HEALTHCHECK_INTERVAL_S * 3, TimeUnit.SECONDS);

        assertThat(testService.reported.size(), equalTo(1));
        assertThat(testService.reported.get(0).getStatus(), equalTo(InstanceInfo.Status.UP));
    }

    class TestEurekaServerHealthService extends EurekaServerHealthService {

        public final List<InstanceInfo> reported = new ArrayList<>();

        public TestEurekaServerHealthService(Scheduler scheduler) {
            super(EurekaServerConfig.baseBuilder().build(), TEST_HEALTHCHECK_INTERVAL_S, scheduler);
        }

        @Override
        protected Func1<InstanceInfo.Builder, InstanceInfo.Builder> resolveServersFunc() {
            return new Func1<InstanceInfo.Builder, InstanceInfo.Builder>() {
                @Override
                public InstanceInfo.Builder call(InstanceInfo.Builder builder) {
                    return builder.withPorts(PORTS);
                }
            };
        }

        @Override
        protected void initHealthCheck() {
            if (initHealthCheckLatch != null) {
                initHealthCheckLatch.countDown();
            }

            if (initHealthCheck.get()) {
                super.initHealthCheck();
            }
        }

        @Override
        public Observable<Void> report(InstanceInfo instanceInfo) {
            reported.add(instanceInfo);
            return Observable.empty();
        }
    }
}
