package com.netflix.eureka.resources;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;

import com.netflix.appinfo.EurekaAccept;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.wrappers.CodecWrappers;
import com.netflix.discovery.converters.wrappers.DecoderWrapper;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.EurekaEntityComparators;
import com.netflix.discovery.util.InstanceInfoGenerator;
import com.netflix.eureka.AbstractTester;
import com.netflix.eureka.Version;
import com.netflix.eureka.registry.Key;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author David Liu
 */
public class AbstractVIPResourceTest extends AbstractTester {
    private String vipName;
    private AbstractVIPResource resource;
    private Application testApplication;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        InstanceInfoGenerator instanceInfos = InstanceInfoGenerator.newBuilder(6, 1).build();
        testApplication = instanceInfos.toApplications().getRegisteredApplications().get(0);

        resource = new AbstractVIPResource(serverContext) {
            @Override
            protected Response getVipResponse(String version, String entityName, String acceptHeader, String ifNonMatched, EurekaAccept eurekaAccept, Key.EntityType entityType) {
                return super.getVipResponse(version, entityName, acceptHeader, ifNonMatched, eurekaAccept, entityType);
            }
        };

        vipName = testApplication.getName() + "#VIP";

        for (InstanceInfo instanceInfo : testApplication.getInstances()) {
            InstanceInfo changed = new InstanceInfo.Builder(instanceInfo)
                    .setASGName(null)  // null asgName to get around AwsAsgUtil check
                    .setVIPAddress(vipName)  // use the same vip address for all the instances in this test
                    .build();
            registry.register(changed, false);
        }
    }

    @Test
    public void testFullVipGet() throws Exception {
        Response response = resource.getVipResponse(
                Version.V2.name(),
                vipName,
                MediaType.APPLICATION_JSON,
                null,
                EurekaAccept.full,
                Key.EntityType.VIP
        );

        byte[] json = (byte[]) response.getEntity();
        DecoderWrapper decoder = CodecWrappers.getDecoder(CodecWrappers.LegacyJacksonJson.class);

        Applications decodedApps = decoder.decode(new ByteArrayInputStream(json), Applications.class);
        Application decodedApp = decodedApps.getRegisteredApplications(testApplication.getName());
        assertThat(EurekaEntityComparators.equal(testApplication, decodedApp), is(true));
    }

    @Test
    public void testMiniVipGet() throws Exception {
        Response response = resource.getVipResponse(
                Version.V2.name(),
                vipName,
                MediaType.APPLICATION_JSON,
                null,
                EurekaAccept.compact,
                Key.EntityType.VIP
        );

        byte[] json = (byte[]) response.getEntity();
        DecoderWrapper decoder = CodecWrappers.getDecoder(CodecWrappers.LegacyJacksonJson.class);

        Applications decodedApps = decoder.decode(new ByteArrayInputStream(json), Applications.class);
        Application decodedApp = decodedApps.getRegisteredApplications(testApplication.getName());
        // assert false as one is mini, so should NOT equal
        assertThat(EurekaEntityComparators.equal(testApplication, decodedApp), is(false));

        for (InstanceInfo instanceInfo : testApplication.getInstances()) {
            InstanceInfo decodedInfo = decodedApp.getByInstanceId(instanceInfo.getId());
            assertThat(EurekaEntityComparators.equalMini(instanceInfo, decodedInfo), is(true));
        }
    }
}
