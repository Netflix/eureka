package com.netflix.eureka.resources;

import com.netflix.appinfo.EurekaAccept;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.wrappers.CodecWrappers;
import com.netflix.discovery.converters.wrappers.DecoderWrapper;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.util.EurekaEntityComparators;
import com.netflix.discovery.util.InstanceInfoGenerator;
import com.netflix.eureka.AbstractTester;
import com.netflix.eureka.Version;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class ApplicationResourceTest extends AbstractTester {
    private ApplicationResource applicationResource;
    private Application testApplication;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        InstanceInfoGenerator instanceInfos = InstanceInfoGenerator.newBuilder(6, 1).build();
        testApplication = instanceInfos.toApplications().getRegisteredApplications().get(0);

        applicationResource = new ApplicationResource(testApplication.getName(), serverContext.getServerConfig(), serverContext.getRegistry());

        for (InstanceInfo instanceInfo : testApplication.getInstances()) {
            registry.register(instanceInfo, false);
        }
    }

    @Test
    public void testFullAppGet() throws Exception {
        Response response = applicationResource.getApplication(
                Version.V2.name(),
                MediaType.APPLICATION_JSON,
                EurekaAccept.full.name()
        );

        String json = String.valueOf(response.getEntity());
        DecoderWrapper decoder = CodecWrappers.getDecoder(CodecWrappers.LegacyJacksonJson.class);

        Application decodedApp = decoder.decode(json, Application.class);
        assertThat(EurekaEntityComparators.equal(testApplication, decodedApp), is(true));
    }

    @Test
    public void testMiniAppGet() throws Exception {
        Response response = applicationResource.getApplication(
                Version.V2.name(),
                MediaType.APPLICATION_JSON,
                EurekaAccept.compact.name()
        );

        String json = String.valueOf(response.getEntity());
        DecoderWrapper decoder = CodecWrappers.getDecoder(CodecWrappers.LegacyJacksonJson.class);

        Application decodedApp = decoder.decode(json, Application.class);
        // assert false as one is mini, so should NOT equal
        assertThat(EurekaEntityComparators.equal(testApplication, decodedApp), is(false));

        for (InstanceInfo instanceInfo : testApplication.getInstances()) {
            InstanceInfo decodedInfo = decodedApp.getByInstanceId(instanceInfo.getId());
            assertThat(EurekaEntityComparators.equalMini(instanceInfo, decodedInfo), is(true));
        }
    }

    @Test
    public void testGoodRegistration() throws Exception {
        InstanceInfo noIdInfo = InstanceInfoGenerator.takeOne();
        Response response = applicationResource.addInstance(noIdInfo, false+"");
        assertThat(response.getStatus(), is(200));
    }

    @Test
    public void testBadRegistration() throws Exception {
        InstanceInfo instanceInfo = spy(InstanceInfoGenerator.takeOne());
        when(instanceInfo.getId()).thenReturn(null);
        Response response = applicationResource.addInstance(instanceInfo, false+"");
        assertThat(response.getStatus(), is(400));

        instanceInfo = spy(InstanceInfoGenerator.takeOne());
        when(instanceInfo.getHostName()).thenReturn(null);
        response = applicationResource.addInstance(instanceInfo, false+"");
        assertThat(response.getStatus(), is(400));

        instanceInfo = spy(InstanceInfoGenerator.takeOne());
        when(instanceInfo.getAppName()).thenReturn(null);
        response = applicationResource.addInstance(instanceInfo, false+"");
        assertThat(response.getStatus(), is(400));
    }
}
