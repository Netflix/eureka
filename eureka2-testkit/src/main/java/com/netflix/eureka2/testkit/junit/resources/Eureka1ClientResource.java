package com.netflix.eureka2.testkit.junit.resources;

import java.util.Collections;
import java.util.List;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.Builder;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.eureka2.testkit.junit.resources.EurekaExternalResources.EurekaExternalResource;

/**
 * @author Tomasz Bak
 */
public class Eureka1ClientResource extends EurekaExternalResource {

    private final String eurekaPropertyFile;
    private final String appName;
    private final int serverPort;

    private DiscoveryClient eurekaClient;

    public Eureka1ClientResource(String eurekaPropertyFile, String appName, int serverPort) {
        this.eurekaPropertyFile = eurekaPropertyFile;
        this.appName = appName;
        this.serverPort = serverPort;
    }

    @Override
    protected void before() throws Throwable {
        System.setProperty("eureka.client.props", eurekaPropertyFile);

        LeaseInfo leaseInfo = LeaseInfo.Builder.newBuilder()
                .setRenewalIntervalInSecs(1)
                .build();

        DataCenterInfo dataCenterInfo = new DataCenterInfo() {
            @Override
            public Name getName() {
                return Name.MyOwn;
            }
        };

        Builder builder = Builder.newBuilder();
        builder.setAppName(appName);
        builder.setAppGroupName(appName);
        builder.setHostName(appName + ".host");
        builder.setIPAddr("127.0.0.1");
        builder.setDataCenterInfo(dataCenterInfo);
        builder.setLeaseInfo(leaseInfo);
        InstanceInfo instanceInfo = builder.build();

        ApplicationInfoManager manager = new ApplicationInfoManager(new MyDataCenterInstanceConfig(), instanceInfo);

        DefaultEurekaClientConfig config = new DefaultEurekaClientConfig() {
            @Override
            public List<String> getEurekaServerServiceUrls(String myZone) {
                return Collections.singletonList("http://localhost:" + serverPort + "/eureka1/v2/");
            }

            @Override
            public int getRegistryFetchIntervalSeconds() {
                return 1;
            }
        };
        eurekaClient = new DiscoveryClient(manager, config);
    }

    @Override
    protected void after() {
        if (eurekaClient != null) {
            eurekaClient.shutdown();
            eurekaClient = null;
        }
    }

    public DiscoveryClient getEurekaClient() {
        return eurekaClient;
    }
}
