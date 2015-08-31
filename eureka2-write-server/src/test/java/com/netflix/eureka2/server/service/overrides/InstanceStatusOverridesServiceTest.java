package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.registry.ChangeNotificationObservable;
import com.netflix.eureka2.registry.EurekaRegistrationProcessorStub;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Source.Origin;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Builder;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;

/**
* @author Tomasz Bak
*/
public class InstanceStatusOverridesServiceTest {

    private static final InstanceInfo FIRST_INSTANCE_INFO = SampleInstanceInfo.WebServer.builder()
            .withStatus(Status.UP).build();

    private static final Source SOURCE = new Source(Origin.LOCAL, "connection#1");

    private final EurekaRegistrationProcessorStub registrationDelegate = new EurekaRegistrationProcessorStub();
    private final InMemoryStatusOverridesRegistry overridesSource = new InMemoryStatusOverridesRegistry();
    private final InstanceStatusOverridesService overridesService = new InstanceStatusOverridesService(overridesSource);

    private final ChangeNotificationObservable dataStream = ChangeNotificationObservable.create();

    @Before
    public void setUp() throws Exception {
        overridesService.addOutboundHandler(registrationDelegate);
        overridesService.connect(FIRST_INSTANCE_INFO.getId(), SOURCE, dataStream).subscribe();
    }

    @Test
    public void testRegistrationPassesThroughIfNoOverridePresent() throws Exception {
        // First registration
        dataStream.register(FIRST_INSTANCE_INFO);
        registrationDelegate.verifyRegisteredWith(FIRST_INSTANCE_INFO);

        // Now update
        InstanceInfo update = new Builder().withInstanceInfo(FIRST_INSTANCE_INFO).withStatus(Status.DOWN).build();
        dataStream.register(update);
        registrationDelegate.verifyRegisteredWith(update);

        // Now complete
        dataStream.onCompleted();
        registrationDelegate.verifyRegistrationCompleted();
    }

    @Test
    public void testRegistrationEmitsUpdatesWhenOverridesChange() throws Exception {
        InstanceInfo setOOS = new Builder().withInstanceInfo(FIRST_INSTANCE_INFO).withStatus(Status.OUT_OF_SERVICE).build();
        InstanceInfo unsetOOS = new Builder().withInstanceInfo(FIRST_INSTANCE_INFO).withStatus(Status.UP).build();

        // First registration
        dataStream.register(FIRST_INSTANCE_INFO);
        overridesSource.setOutOfService(FIRST_INSTANCE_INFO).subscribe();

        registrationDelegate.verifyRegisteredWith(setOOS);

        // Second combined
        overridesSource.unsetOutOfService(FIRST_INSTANCE_INFO).subscribe();
        registrationDelegate.verifyRegisteredWith(unsetOOS);
    }
}