package com.netflix.eureka2.model.toplogy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfo.Builder;
import com.netflix.eureka2.model.instance.InstanceInfo.Status;
import com.netflix.eureka2.testkit.data.builder.SampleServicePort;

/**
 * @author Tomasz Bak
 */
public class Service {

    private final Application application;
    private final InstanceInfo selfInfo;

    public Service(Application application, InstanceInfo selfInfo) {
        this.application = application;
        this.selfInfo = selfInfo;
    }

    public Application getApplication() {
        return application;
    }

    public InstanceInfo getSelfInfo() {
        return selfInfo;
    }

    public static Service createServiceOf(String serverId, Application application, DataCenterInfoGenerator dataCenterInfoGenerator) {
        String id = application.getServiceName();

        HashSet<String> healthcheckURLs = new HashSet<>();
        healthcheckURLs.add("TODO");

        Map<String, String> metaData = new HashMap<>();
        metaData.put("key1", "value1");
        Builder builder = new Builder()
                .withId(id)
                .withApp(application.getName())
                .withAppGroup(application.getName() + "_group")
                .withAsg(application.getName() + "_asg")
                .withDataCenterInfo(dataCenterInfoGenerator.next())
                .withHealthCheckUrls(healthcheckURLs)
                .withHomePageUrl("TODO")
                .withMetaData(metaData)
                .withPorts(SampleServicePort.httpPorts())
                .withVipAddress(application.getName() + "_vip")
                .withSecureVipAddress(application.getName() + "_secureVip")
                .withStatus(Status.UP)
                .withStatusPageUrl("TODO");
        if (serverId != null) {
            builder.withMetaData(TopologyFunctions.SERVER_ID_KEY, serverId);
        }
        InstanceInfo selfInfo = builder.build();

        return new Service(application, selfInfo);
    }
}
