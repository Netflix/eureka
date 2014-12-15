package netflix.admin.eureka;

import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.datacenter.AwsDataCenterInfo;
import org.junit.rules.ExternalResource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MockRegistryResource extends ExternalResource {
    private int instIdPrefix = 0;

    private InstanceInfo makeInstInfo(String app) {
        instIdPrefix++;
        return new InstanceInfo.Builder()
                .withApp(app)
                .withId(app + "-id-" + instIdPrefix)
                .withStatus(InstanceInfo.Status.UP)
                .withDataCenterInfo(new AwsDataCenterInfo.Builder()
                        .withInstanceId("aws-inst-id-" + instIdPrefix)
                        .withInstanceType("bigone")
                        .withRegion("us-east-1")
                        .withZone("us-east-1d")
                        .withPublicHostName("public-host-name").build()).build();
    }

    protected Map<String, InstanceInfo> makeInstanceInfoMap() {
        Map<String, InstanceInfo> registryMap = new HashMap<>();
        for (String appId : Arrays.asList("App_1", "App_2", "App_3", "App_3", "App_1")) {
            final InstanceInfo instInfo = makeInstInfo(appId);
            registryMap.put(instInfo.getId(), instInfo);
        }
        return registryMap;
    }
}
