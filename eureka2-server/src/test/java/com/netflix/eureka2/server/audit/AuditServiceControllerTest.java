package com.netflix.eureka2.server.audit;

import com.google.inject.util.Providers;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.testkit.data.builder.SampleChangeNotification;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import rx.Observable;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Tomasz Bak
 */
public class AuditServiceControllerTest {

    private static final InstanceInfo SELF_INFO = SampleInstanceInfo.EurekaWriteServer.build();
    private static final ChangeNotification<InstanceInfo> SOME_APP_ADD = SampleChangeNotification.DiscoveryAdd.newNotification();

    private final EurekaRegistry<InstanceInfo> registry = mock(EurekaRegistry.class);
    private final AuditService auditService = mock(AuditService.class);
    private final SelfInfoResolver selfInfoResolver = mock(SelfInfoResolver.class);

    @Before
    public void setUp() throws Exception {
        when(selfInfoResolver.resolve()).thenReturn(Observable.just(SELF_INFO));
        when(registry.forInterest(any(Interest.class))).thenReturn(Observable.just(SOME_APP_ADD));
    }

    @Test
    public void testWritesAuditEntryWithOwnServerId() throws Exception {
        AuditServiceController controller = new AuditServiceController(registry, auditService, Providers.of(selfInfoResolver));
        controller.startRegistryAuditing();

        ArgumentCaptor<AuditRecord> recordCaptor = ArgumentCaptor.forClass(AuditRecord.class);

        verify(auditService, times(1)).write(recordCaptor.capture());
        AuditRecord capturedRecord = recordCaptor.getValue();

        assertThat(capturedRecord.getAuditServerId(), is(equalTo(SELF_INFO.getId())));
        assertThat(capturedRecord.getInstanceInfo(), is(equalTo(SOME_APP_ADD.getData())));
    }
}