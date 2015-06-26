package com.netflix.eureka2.ext.aws;

import com.netflix.eureka2.registry.EurekaRegistrationProcessorStub;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Source.Origin;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Builder;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class AsgOverrideServiceTest {

    private static final InstanceInfo FIRST_INSTANCE_INFO = SampleInstanceInfo.WebServer.build();
    private static final Source SOURCE = new Source(Origin.LOCAL, "connection#1");

    private final AsgStatusRegistry asgStatusRegistry = mock(AsgStatusRegistry.class);

    private final EurekaRegistrationProcessorStub registrationDelegate = new EurekaRegistrationProcessorStub();

    private final AsgOverrideService asgOverrideService = new AsgOverrideService(asgStatusRegistry);

    private final BehaviorSubject<Boolean> statusUpdateSubject = BehaviorSubject.create();
    private final PublishSubject<InstanceInfo> registrationSubject = PublishSubject.create();

    @Before
    public void setUp() throws Exception {
        asgOverrideService.addOutboundHandler(registrationDelegate);

        statusUpdateSubject.onNext(true); // Default
        when(asgStatusRegistry.asgStatusUpdates(anyString())).thenReturn(statusUpdateSubject);
        asgOverrideService.register(FIRST_INSTANCE_INFO.getId(), registrationSubject, SOURCE).subscribe();
    }

    @Test
    public void testRegistrationPassesThroughIfNoOverridePresent() throws Exception {
        // First registration
        registrationSubject.onNext(FIRST_INSTANCE_INFO);
        registrationDelegate.verifyRegisteredWith(FIRST_INSTANCE_INFO);

        // Now update
        InstanceInfo update = new Builder().withInstanceInfo(FIRST_INSTANCE_INFO).withStatus(Status.DOWN).build();
        registrationSubject.onNext(update);

        registrationDelegate.verifyRegisteredWith(update);

        // Now complete
        registrationSubject.onCompleted();
        registrationDelegate.verifyRegistrationCompleted();
    }

    @Test
    public void testRegistrationEmitsUpdatesWhenOverridesChange() throws Exception {
        InstanceInfo secondCombined = new Builder().withInstanceInfo(FIRST_INSTANCE_INFO).withStatus(Status.OUT_OF_SERVICE).build();

        // First registration
        registrationSubject.onNext(FIRST_INSTANCE_INFO);
        registrationDelegate.verifyRegisteredWith(FIRST_INSTANCE_INFO);

        // Now ASG status change
        statusUpdateSubject.onNext(false);
        registrationDelegate.verifyRegisteredWith(secondCombined);
    }
}