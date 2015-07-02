package netflix.admin.eureka;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import netflix.adminresources.resources.eureka.registry.InstanceInfoSummary;
import org.junit.rules.ExternalResource;

public class MockRegistryResource extends ExternalResource {
    private int instIdPrefix = 0;

    private InstanceInfoSummary makeInstInfo(String app) {
        instIdPrefix++;
        return new InstanceInfoSummary(
                app,
                app + "-id-" + instIdPrefix,
                Status.UP,
                "vip#" + app,
                "10.0.0." + instIdPrefix,
                "server" + instIdPrefix + ".test.net"
        );
    }

    protected Map<String, InstanceInfoSummary> makeInstanceInfoMap() {
        Map<String, InstanceInfoSummary> registryMap = new HashMap<>();
        for (String appId : Arrays.asList("App_1", "App_2", "App_3", "App_3", "App_1")) {
            final InstanceInfoSummary instInfo = makeInstInfo(appId);
            registryMap.put(instInfo.getInstanceId(), instInfo);
        }
        return registryMap;
    }
}
