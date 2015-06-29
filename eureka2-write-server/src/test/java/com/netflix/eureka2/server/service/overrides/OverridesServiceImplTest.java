package com.netflix.eureka2.server.service.overrides;

import java.util.Collections;
import java.util.Set;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.registry.EurekaRegistrationProcessorStub;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Source.Origin;
import com.netflix.eureka2.registry.instance.Delta;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Builder;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.subjects.PublishSubject;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class OverridesServiceImplTest {

    private static final InstanceInfo FIRST_INSTANCE_INFO = SampleInstanceInfo.WebServer.build();
    private static final Source SOURCE = new Source(Origin.LOCAL, "connection#1");

    private final EurekaRegistrationProcessorStub registrationDelegate = new EurekaRegistrationProcessorStub();

    private final OverridesRegistry overridesRegistry = mock(OverridesRegistry.class);

    private final OverridesServiceImpl overridesService = new OverridesServiceImpl(overridesRegistry);

    private final PublishSubject<ChangeNotification<Overrides>> overridesSubject = PublishSubject.create();
    private final PublishSubject<InstanceInfo> registrationSubject = PublishSubject.create();

    @Before
    public void setUp() throws Exception {
        overridesService.addOutboundHandler(registrationDelegate);

        when(overridesRegistry.forUpdates(anyString())).thenReturn(overridesSubject);
        overridesService.register(FIRST_INSTANCE_INFO.getId(), registrationSubject, SOURCE).subscribe();
    }

    @Test
    public void testRegistrationPassesThroughIfNoOverridePresent() throws Exception {
        // First registration
        registrationSubject.onNext(FIRST_INSTANCE_INFO);
        overridesSubject.onNext(new ChangeNotification<Overrides>(Kind.Delete, new Overrides(FIRST_INSTANCE_INFO.getId(), Collections.<Delta<?>>emptySet())));

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
        InstanceInfo firstCombined = new Builder().withInstanceInfo(FIRST_INSTANCE_INFO).withStatus(Status.STARTING).build();
        Set<Delta<?>> firstOverride = firstCombined.diffOlder(FIRST_INSTANCE_INFO);
        InstanceInfo secondCombined = new Builder().withInstanceInfo(FIRST_INSTANCE_INFO).withStatus(Status.DOWN).build();
        Set<Delta<?>> secondOverride = secondCombined.diffOlder(FIRST_INSTANCE_INFO);

        // First registration
        registrationSubject.onNext(FIRST_INSTANCE_INFO);
        overridesSubject.onNext(new ChangeNotification<Overrides>(Kind.Add, new Overrides(FIRST_INSTANCE_INFO.getId(), firstOverride)));

        registrationDelegate.verifyRegisteredWith(firstCombined);

        // Second combined
        overridesSubject.onNext(new ChangeNotification<Overrides>(Kind.Add, new Overrides(FIRST_INSTANCE_INFO.getId(), secondOverride)));
        registrationDelegate.verifyRegisteredWith(secondCombined);
    }
}