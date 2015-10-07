package com.netflix.discovery.converters;

import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.discovery.converters.jackson.AbstractEurekaJacksonCodec;
import com.netflix.discovery.converters.jackson.EurekaJsonJacksonCodec;
import com.netflix.discovery.converters.jackson.EurekaXmlJacksonCodec;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.EurekaEntityComparators;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class EurekaJsonAndXmlJacksonCodecTest {

    private final InstanceInfoGenerator infoGenerator = InstanceInfoGenerator.newBuilder(4, 2).withMetaData(true).build();
    private final Iterator<InstanceInfo> infoIterator = infoGenerator.serviceIterator();

    @Test
    public void testAmazonInfoEncodeDecodeWithJson() throws Exception {
        doAmazonInfoEncodeDecodeTest(new EurekaJsonJacksonCodec());
    }

    @Test
    public void testAmazonInfoEncodeDecodeWithXml() throws Exception {
        doAmazonInfoEncodeDecodeTest(new EurekaXmlJacksonCodec());
    }

    private void doAmazonInfoEncodeDecodeTest(AbstractEurekaJacksonCodec codec) throws Exception {
        AmazonInfo amazonInfo = (AmazonInfo) infoIterator.next().getDataCenterInfo();
        String encodedString = codec.getObjectMapper(DataCenterInfo.class).writeValueAsString(amazonInfo);

        DataCenterInfo decodedValue = codec.getObjectMapper(DataCenterInfo.class).readValue(encodedString, DataCenterInfo.class);
        assertThat(EurekaEntityComparators.equal(amazonInfo, decodedValue), is(true));
    }

    @Test
    public void testAmazonInfoCompactEncodeDecodeWithJson() throws Exception {
        doAmazonInfoCompactEncodeDecodeTest(new EurekaJsonJacksonCodec(KeyFormatter.defaultKeyFormatter(), true));
    }

    @Test
    public void testAmazonInfoCompactEncodeDecodeWithXml() throws Exception {
        doAmazonInfoCompactEncodeDecodeTest(new EurekaXmlJacksonCodec(KeyFormatter.defaultKeyFormatter(), true));
    }

    private void doAmazonInfoCompactEncodeDecodeTest(AbstractEurekaJacksonCodec codec) throws Exception {
        AmazonInfo amazonInfo = (AmazonInfo) infoIterator.next().getDataCenterInfo();
        String encodedString = codec.getObjectMapper(DataCenterInfo.class).writeValueAsString(amazonInfo);

        AmazonInfo decodedValue = (AmazonInfo) codec.getObjectMapper(DataCenterInfo.class).readValue(encodedString, DataCenterInfo.class);

        assertThat(decodedValue.get(MetaDataKey.publicHostname), is(equalTo(amazonInfo.get(MetaDataKey.publicHostname))));
    }

    @Test
    public void testMyDataCenterInfoEncodeDecodeWithJson() throws Exception {
        doMyDataCenterInfoEncodeDecodeTest(new EurekaJsonJacksonCodec());
    }

    @Test
    public void testMyDataCenterInfoEncodeDecodeWithXml() throws Exception {
        doMyDataCenterInfoEncodeDecodeTest(new EurekaXmlJacksonCodec());
    }

    private void doMyDataCenterInfoEncodeDecodeTest(AbstractEurekaJacksonCodec codec) throws Exception {
        DataCenterInfo myDataCenterInfo = new DataCenterInfo() {
            @Override
            public Name getName() {
                return Name.MyOwn;
            }
        };

        String encodedString = codec.getObjectMapper(DataCenterInfo.class).writeValueAsString(myDataCenterInfo);
        DataCenterInfo decodedValue = codec.getObjectMapper(DataCenterInfo.class).readValue(encodedString, DataCenterInfo.class);
        assertThat(decodedValue.getName(), is(equalTo(Name.MyOwn)));
    }

    @Test
    public void testLeaseInfoEncodeDecodeWithJson() throws Exception {
        doLeaseInfoEncodeDecode(new EurekaJsonJacksonCodec());
    }

    @Test
    public void testLeaseInfoEncodeDecodeWithXml() throws Exception {
        doLeaseInfoEncodeDecode(new EurekaXmlJacksonCodec());
    }

    private void doLeaseInfoEncodeDecode(AbstractEurekaJacksonCodec codec) throws Exception {
        LeaseInfo leaseInfo = infoIterator.next().getLeaseInfo();

        String encodedString = codec.getObjectMapper(LeaseInfo.class).writeValueAsString(leaseInfo);
        LeaseInfo decodedValue = codec.getObjectMapper(LeaseInfo.class).readValue(encodedString, LeaseInfo.class);
        assertThat(EurekaEntityComparators.equal(leaseInfo, decodedValue), is(true));
    }

    @Test
    public void testInstanceInfoEncodeDecodeWithJson() throws Exception {
        doInstanceInfoEncodeDecode(new EurekaJsonJacksonCodec());
    }

    @Test
    public void testInstanceInfoEncodeDecodeWithXml() throws Exception {
        doInstanceInfoEncodeDecode(new EurekaXmlJacksonCodec());
    }

    private void doInstanceInfoEncodeDecode(AbstractEurekaJacksonCodec codec) throws Exception {
        InstanceInfo instanceInfo = infoIterator.next();

        String encodedString = codec.getObjectMapper(InstanceInfo.class).writeValueAsString(instanceInfo);
        InstanceInfo decodedValue = codec.getObjectMapper(InstanceInfo.class).readValue(encodedString, InstanceInfo.class);
        assertThat(EurekaEntityComparators.equal(instanceInfo, decodedValue), is(true));
    }

    @Test
    public void testInstanceInfoCompactEncodeDecodeWithJson() throws Exception {
        doInstanceInfoCompactEncodeDecode(new EurekaJsonJacksonCodec(KeyFormatter.defaultKeyFormatter(), true), true);
    }

    @Test
    public void testInstanceInfoCompactEncodeDecodeWithXml() throws Exception {
        doInstanceInfoCompactEncodeDecode(new EurekaXmlJacksonCodec(KeyFormatter.defaultKeyFormatter(), true), false);
    }

    private void doInstanceInfoCompactEncodeDecode(AbstractEurekaJacksonCodec codec, boolean isJson) throws Exception {
        InstanceInfo instanceInfo = infoIterator.next();

        String encodedString = codec.getObjectMapper(InstanceInfo.class).writeValueAsString(instanceInfo);

        if (isJson) {
            JsonNode metadataNode = new ObjectMapper().readTree(encodedString).get("instance").get("metadata");
            assertThat(metadataNode, is(nullValue()));
        }

        InstanceInfo decodedValue = codec.getObjectMapper(InstanceInfo.class).readValue(encodedString, InstanceInfo.class);

        assertThat(decodedValue.getId(), is(equalTo(instanceInfo.getId())));
        assertThat(decodedValue.getMetadata().isEmpty(), is(true));
    }

    @Test
    public void testInstanceInfoIgnoredFieldsAreFilteredOutDuringDeserializationProcessWithJson() throws Exception {
        doInstanceInfoIgnoredFieldsAreFilteredOutDuringDeserializationProcess(
                new EurekaJsonJacksonCodec(),
                new EurekaJsonJacksonCodec(KeyFormatter.defaultKeyFormatter(), true)
        );
    }

    @Test
    public void testInstanceInfoIgnoredFieldsAreFilteredOutDuringDeserializationProcessWithXml() throws Exception {
        doInstanceInfoIgnoredFieldsAreFilteredOutDuringDeserializationProcess(
                new EurekaXmlJacksonCodec(),
                new EurekaXmlJacksonCodec(KeyFormatter.defaultKeyFormatter(), true)
        );
    }

    public void doInstanceInfoIgnoredFieldsAreFilteredOutDuringDeserializationProcess(AbstractEurekaJacksonCodec fullCodec,
                                                                                      AbstractEurekaJacksonCodec compactCodec) throws Exception {
        InstanceInfo instanceInfo = infoIterator.next();

        // We use regular codec here to have all fields serialized
        String encodedString = fullCodec.getObjectMapper(InstanceInfo.class).writeValueAsString(instanceInfo);
        InstanceInfo decodedValue = compactCodec.getObjectMapper(InstanceInfo.class).readValue(encodedString, InstanceInfo.class);

        assertThat(decodedValue.getId(), is(equalTo(instanceInfo.getId())));
        assertThat(decodedValue.getAppName(), is(equalTo(instanceInfo.getAppName())));
        assertThat(decodedValue.getIPAddr(), is(equalTo(instanceInfo.getIPAddr())));
        assertThat(decodedValue.getVIPAddress(), is(equalTo(instanceInfo.getVIPAddress())));
        assertThat(decodedValue.getSecureVipAddress(), is(equalTo(instanceInfo.getSecureVipAddress())));
        assertThat(decodedValue.getHostName(), is(equalTo(instanceInfo.getHostName())));
        assertThat(decodedValue.getStatus(), is(equalTo(instanceInfo.getStatus())));
        assertThat(decodedValue.getActionType(), is(equalTo(instanceInfo.getActionType())));
        assertThat(decodedValue.getASGName(), is(equalTo(instanceInfo.getASGName())));
        assertThat(decodedValue.getLastUpdatedTimestamp(), is(equalTo(instanceInfo.getLastUpdatedTimestamp())));

        AmazonInfo sourceAmazonInfo = (AmazonInfo) instanceInfo.getDataCenterInfo();
        AmazonInfo decodedAmazonInfo = (AmazonInfo) decodedValue.getDataCenterInfo();
        assertThat(decodedAmazonInfo.get(MetaDataKey.accountId), is(equalTo(sourceAmazonInfo.get(MetaDataKey.accountId))));
    }

    @Test
    public void testInstanceInfoWithNoMetaEncodeDecodeWithJson() throws Exception {
        doInstanceInfoWithNoMetaEncodeDecode(new EurekaJsonJacksonCodec(), true);
    }

    @Test
    public void testInstanceInfoWithNoMetaEncodeDecodeWithXml() throws Exception {
        doInstanceInfoWithNoMetaEncodeDecode(new EurekaXmlJacksonCodec(), false);
    }

    private void doInstanceInfoWithNoMetaEncodeDecode(AbstractEurekaJacksonCodec codec, boolean json) throws Exception {
        InstanceInfo noMetaDataInfo = new InstanceInfo.Builder(infoIterator.next()).setMetadata(null).build();

        String encodedString = codec.getObjectMapper(InstanceInfo.class).writeValueAsString(noMetaDataInfo);

        // Backward compatibility with old codec
        if (json) {
            assertThat(encodedString.contains("\"@class\":\"java.util.Collections$EmptyMap\""), is(true));
        }

        InstanceInfo decodedValue = codec.getObjectMapper(InstanceInfo.class).readValue(encodedString, InstanceInfo.class);
        assertThat(decodedValue.getId(), is(equalTo(noMetaDataInfo.getId())));
        assertThat(decodedValue.getMetadata().isEmpty(), is(true));
    }

    @Test
    public void testApplicationEncodeDecodeWithJson() throws Exception {
        doApplicationEncodeDecode(new EurekaJsonJacksonCodec());
    }

    @Test
    public void testApplicationEncodeDecodeWithXml() throws Exception {
        doApplicationEncodeDecode(new EurekaXmlJacksonCodec());
    }

    private void doApplicationEncodeDecode(AbstractEurekaJacksonCodec codec) throws Exception {
        Application application = new Application("testApp");
        application.addInstance(infoIterator.next());
        application.addInstance(infoIterator.next());

        String encodedString = codec.getObjectMapper(Application.class).writeValueAsString(application);
        Application decodedValue = codec.getObjectMapper(Application.class).readValue(encodedString, Application.class);
        assertThat(EurekaEntityComparators.equal(application, decodedValue), is(true));
    }

    @Test
    public void testApplicationsEncodeDecodeWithJson() throws Exception {
        doApplicationsEncodeDecode(new EurekaJsonJacksonCodec());
    }

    @Test
    public void testApplicationsEncodeDecodeWithXml() throws Exception {
        doApplicationsEncodeDecode(new EurekaXmlJacksonCodec());
    }

    private void doApplicationsEncodeDecode(AbstractEurekaJacksonCodec codec) throws Exception {
        Applications applications = infoGenerator.takeDelta(2);

        String encodedString = codec.getObjectMapper(Applications.class).writeValueAsString(applications);
        Applications decodedValue = codec.getObjectMapper(Applications.class).readValue(encodedString, Applications.class);
        assertThat(EurekaEntityComparators.equal(applications, decodedValue), is(true));
    }
}