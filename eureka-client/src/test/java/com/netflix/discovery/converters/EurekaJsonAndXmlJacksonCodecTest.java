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
        doAmazonInfoEncodeDecodeTest(new EurekaJsonJacksonCodec().getObjectMapper());
    }

    @Test
    public void testAmazonInfoEncodeDecodeWithXml() throws Exception {
        doAmazonInfoEncodeDecodeTest(new EurekaXmlJacksonCodec().getObjectMapper());
    }

    private void doAmazonInfoEncodeDecodeTest(ObjectMapper mapper) throws Exception {
        AmazonInfo amazonInfo = (AmazonInfo) infoIterator.next().getDataCenterInfo();
        String encodedString = mapper.writeValueAsString(amazonInfo);

        DataCenterInfo decodedValue = mapper.readValue(encodedString, DataCenterInfo.class);
        assertThat(EurekaEntityComparators.equal(amazonInfo, decodedValue), is(true));
    }

    @Test
    public void testAmazonInfoCompactEncodeDecodeWithJson() throws Exception {
        doAmazonInfoCompactEncodeDecodeTest(new EurekaJsonJacksonCodec(KeyFormatter.defaultKeyFormatter(), true).getObjectMapper());
    }

    @Test
    public void testAmazonInfoCompactEncodeDecodeWithXml() throws Exception {
        doAmazonInfoCompactEncodeDecodeTest(new EurekaXmlJacksonCodec(KeyFormatter.defaultKeyFormatter(), true).getObjectMapper());
    }

    private void doAmazonInfoCompactEncodeDecodeTest(ObjectMapper mapper) throws Exception {
        AmazonInfo amazonInfo = (AmazonInfo) infoIterator.next().getDataCenterInfo();
        String encodedString = mapper.writeValueAsString(amazonInfo);

        AmazonInfo decodedValue = (AmazonInfo) mapper.readValue(encodedString, DataCenterInfo.class);

        assertThat(decodedValue.get(MetaDataKey.publicHostname), is(equalTo(amazonInfo.get(MetaDataKey.publicHostname))));
    }

    @Test
    public void testMyDataCenterInfoEncodeDecodeWithJson() throws Exception {
        doMyDataCenterInfoEncodeDecodeTest(new EurekaJsonJacksonCodec().getObjectMapper());
    }

    @Test
    public void testMyDataCenterInfoEncodeDecodeWithXml() throws Exception {
        doMyDataCenterInfoEncodeDecodeTest(new EurekaXmlJacksonCodec().getObjectMapper());
    }

    private void doMyDataCenterInfoEncodeDecodeTest(ObjectMapper mapper) throws Exception {
        DataCenterInfo myDataCenterInfo = new DataCenterInfo() {
            @Override
            public Name getName() {
                return Name.MyOwn;
            }
        };

        String encodedString = mapper.writeValueAsString(myDataCenterInfo);
        DataCenterInfo decodedValue = mapper.readValue(encodedString, DataCenterInfo.class);
        assertThat(decodedValue.getName(), is(equalTo(Name.MyOwn)));
    }

    @Test
    public void testLeaseInfoEncodeDecodeWithJson() throws Exception {
        doLeaseInfoEncodeDecode(new EurekaJsonJacksonCodec().getObjectMapper());
    }

    @Test
    public void testLeaseInfoEncodeDecodeWithXml() throws Exception {
        doLeaseInfoEncodeDecode(new EurekaXmlJacksonCodec().getObjectMapper());
    }

    private void doLeaseInfoEncodeDecode(ObjectMapper mapper) throws Exception {
        LeaseInfo leaseInfo = infoIterator.next().getLeaseInfo();

        String encodedString = mapper.writeValueAsString(leaseInfo);
        LeaseInfo decodedValue = mapper.readValue(encodedString, LeaseInfo.class);
        assertThat(EurekaEntityComparators.equal(leaseInfo, decodedValue), is(true));
    }

    @Test
    public void testInstanceInfoEncodeDecodeWithJson() throws Exception {
        doInstanceInfoEncodeDecode(new EurekaJsonJacksonCodec().getObjectMapper());
    }

    @Test
    public void testInstanceInfoEncodeDecodeWithXml() throws Exception {
        doInstanceInfoEncodeDecode(new EurekaXmlJacksonCodec().getObjectMapper());
    }

    private void doInstanceInfoEncodeDecode(ObjectMapper mapper) throws Exception {
        InstanceInfo instanceInfo = infoIterator.next();

        String encodedString = mapper.writeValueAsString(instanceInfo);
        InstanceInfo decodedValue = mapper.readValue(encodedString, InstanceInfo.class);
        assertThat(EurekaEntityComparators.equal(instanceInfo, decodedValue), is(true));
    }

    @Test
    public void testInstanceInfoCompactEncodeDecodeWithJson() throws Exception {
        doInstanceInfoCompactEncodeDecode(new EurekaJsonJacksonCodec(KeyFormatter.defaultKeyFormatter(), true).getObjectMapper(), true);
    }

    @Test
    public void testInstanceInfoCompactEncodeDecodeWithXml() throws Exception {
        doInstanceInfoCompactEncodeDecode(new EurekaXmlJacksonCodec(KeyFormatter.defaultKeyFormatter(), true).getObjectMapper(), false);
    }

    private void doInstanceInfoCompactEncodeDecode(ObjectMapper mapper, boolean isJson) throws Exception {
        InstanceInfo instanceInfo = infoIterator.next();

        String encodedString = mapper.writeValueAsString(instanceInfo);

        if (isJson) {
            JsonNode metadataNode = new ObjectMapper().readTree(encodedString).get("instance").get("metadata");
            assertThat(metadataNode, is(nullValue()));
        }

        InstanceInfo decodedValue = mapper.readValue(encodedString, InstanceInfo.class);

        assertThat(decodedValue.getId(), is(equalTo(instanceInfo.getId())));
        assertThat(decodedValue.getMetadata().isEmpty(), is(true));
    }

    @Test
    public void testInstanceInfoIgnoredFieldsAreFilteredOutDuringDeserializationProcessWithJson() throws Exception {
        doInstanceInfoIgnoredFieldsAreFilteredOutDuringDeserializationProcess(
                new EurekaJsonJacksonCodec().getObjectMapper(),
                new EurekaJsonJacksonCodec(KeyFormatter.defaultKeyFormatter(), true).getObjectMapper()
        );
    }

    @Test
    public void testInstanceInfoIgnoredFieldsAreFilteredOutDuringDeserializationProcessWithXml() throws Exception {
        doInstanceInfoIgnoredFieldsAreFilteredOutDuringDeserializationProcess(
                new EurekaXmlJacksonCodec().getObjectMapper(),
                new EurekaXmlJacksonCodec(KeyFormatter.defaultKeyFormatter(), true).getObjectMapper()
        );
    }

    public void doInstanceInfoIgnoredFieldsAreFilteredOutDuringDeserializationProcess(ObjectMapper fullMapper, ObjectMapper compactMapper) throws Exception {
        InstanceInfo instanceInfo = infoIterator.next();

        // We use regular codec here to have all fields serialized
        String encodedString = fullMapper.writeValueAsString(instanceInfo);
        InstanceInfo decodedValue = compactMapper.readValue(encodedString, InstanceInfo.class);

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
        doInstanceInfoWithNoMetaEncodeDecode(new EurekaJsonJacksonCodec().getObjectMapper(), true);
    }

    @Test
    public void testInstanceInfoWithNoMetaEncodeDecodeWithXml() throws Exception {
        doInstanceInfoWithNoMetaEncodeDecode(new EurekaXmlJacksonCodec().getObjectMapper(), false);
    }

    private void doInstanceInfoWithNoMetaEncodeDecode(ObjectMapper mapper, boolean json) throws Exception {
        InstanceInfo noMetaDataInfo = new InstanceInfo.Builder(infoIterator.next()).setMetadata(null).build();

        String encodedString = mapper.writeValueAsString(noMetaDataInfo);

        // Backward compatibility with old codec
        if (json) {
            assertThat(encodedString.contains("\"@class\":\"java.util.Collections$EmptyMap\""), is(true));
        }

        InstanceInfo decodedValue = mapper.readValue(encodedString, InstanceInfo.class);
        assertThat(decodedValue.getId(), is(equalTo(noMetaDataInfo.getId())));
        assertThat(decodedValue.getMetadata().isEmpty(), is(true));
    }

    @Test
    public void testApplicationEncodeDecodeWithJson() throws Exception {
        doApplicationEncodeDecode(new EurekaJsonJacksonCodec().getObjectMapper());
    }

    @Test
    public void testApplicationEncodeDecodeWithXml() throws Exception {
        doApplicationEncodeDecode(new EurekaXmlJacksonCodec().getObjectMapper());
    }

    private void doApplicationEncodeDecode(ObjectMapper mapper) throws Exception {
        Application application = new Application("testApp");
        application.addInstance(infoIterator.next());
        application.addInstance(infoIterator.next());

        String encodedString = mapper.writeValueAsString(application);
        Application decodedValue = mapper.readValue(encodedString, Application.class);
        assertThat(EurekaEntityComparators.equal(application, decodedValue), is(true));
    }

    @Test
    public void testApplicationsEncodeDecodeWithJson() throws Exception {
        doApplicationsEncodeDecode(new EurekaJsonJacksonCodec().getObjectMapper());
    }

    @Test
    public void testApplicationsEncodeDecodeWithXml() throws Exception {
        doApplicationsEncodeDecode(new EurekaXmlJacksonCodec().getObjectMapper());
    }

    private void doApplicationsEncodeDecode(ObjectMapper mapper) throws Exception {
        Applications applications = infoGenerator.takeDelta(2);

        String encodedString = mapper.writeValueAsString(applications);
        Applications decodedValue = mapper.readValue(encodedString, Applications.class);
        assertThat(EurekaEntityComparators.equal(applications, decodedValue), is(true));
    }
}