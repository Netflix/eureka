package com.netflix.eureka2;

import java.util.Arrays;
import java.util.List;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.datacenter.AwsDataCenterInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleChangeNotification;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import rx.Observable;

import static org.mockito.Mockito.when;

public class RegistryTestBase {

    public static final String ZUUL = "zuul";

    @Mock
    protected EurekaClient eurekaClient;

    private InstanceInfo instance(String app, int instId) {
        final AwsDataCenterInfo awsDataCenterInfo = new AwsDataCenterInfo.Builder().withInstanceId("Inst-" + instId).build();
        return new InstanceInfo.Builder()
                .withApp(app)
                .withId("id#-" + instId)
                .withVipAddress(app + ":8080")
                .withStatus(InstanceInfo.Status.UP)
                .withDataCenterInfo(awsDataCenterInfo).build();
    }

    private Observable<ChangeNotification<InstanceInfo>> buildMockEurekaRegistryObservable() {
        final List<ChangeNotification<InstanceInfo>> notifications = Arrays.asList(
                SampleChangeNotification.ZuulAdd.newNotification(instance(ZUUL, 1)),
                SampleChangeNotification.ZuulAdd.newNotification(instance(ZUUL, 2)),
                SampleChangeNotification.ZuulAdd.newNotification(instance(ZUUL, 3)),
                SampleChangeNotification.ZuulAdd.newNotification(instance(ZUUL, 4)));
        return Observable.from(notifications);
    }

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        when(eurekaClient.forInterest(Interests.forFullRegistry())).thenReturn(buildMockEurekaRegistryObservable());
    }
}
