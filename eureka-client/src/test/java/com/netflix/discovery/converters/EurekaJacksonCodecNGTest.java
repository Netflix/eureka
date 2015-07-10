package com.netflix.discovery.converters;

import java.util.Iterator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.discovery.converters.jackson.EurekaJacksonCodecNG;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class EurekaJacksonCodecNGTest {

    private final InstanceInfoGenerator infoGenerator = InstanceInfoGenerator.newBuilder(4, 2).withMetaData(true).build();
    private final Iterator<InstanceInfo> infoIterator = infoGenerator.serviceIterator();

    private final EurekaJacksonCodecNG codec = new EurekaJacksonCodecNG();
    private final EurekaJacksonCodecNG compactCodec = new EurekaJacksonCodecNG(KeyFormatter.defaultKeyFormatter(), true);

    @Test
    public void testAmazonInfoEncodeDecodeWithJson() throws Exception {
        doAmazonInfoEncodeDecodeTest(codec.getJsonMapper());
    }

    @Test
    public void testAmazonInfoEncodeDecodeWithXml() throws Exception {
        doAmazonInfoEncodeDecodeTest(codec.getXmlMapper());
    }

    private void doAmazonInfoEncodeDecodeTest(ObjectMapper mapper) throws Exception {
        AmazonInfo amazonInfo = (AmazonInfo) infoIterator.next().getDataCenterInfo();
        String encodedString = mapper.writeValueAsString(amazonInfo);

        DataCenterInfo decodedValue = mapper.readValue(encodedString, DataCenterInfo.class);
        assertThat(EurekaEntityComparators.equal(amazonInfo, decodedValue), is(true));
    }

    @Test
    public void testAmazonInfoCompactEncodeDecodeWithJson() throws Exception {
        doAmazonInfoCompactEncodeDecodeTest(compactCodec.getJsonMapper());
    }

    @Test
    public void testAmazonInfoCompactEncodeDecodeWithXml() throws Exception {
        doAmazonInfoCompactEncodeDecodeTest(compactCodec.getXmlMapper());
    }

    private void doAmazonInfoCompactEncodeDecodeTest(ObjectMapper mapper) throws Exception {
        AmazonInfo amazonInfo = (AmazonInfo) infoIterator.next().getDataCenterInfo();
        String encodedString = mapper.writeValueAsString(amazonInfo);

        AmazonInfo decodedValue = (AmazonInfo) mapper.readValue(encodedString, DataCenterInfo.class);

        assertThat(decodedValue.get(MetaDataKey.publicHostname), is(equalTo(amazonInfo.get(MetaDataKey.publicHostname))));
    }

    @Test
    public void testMyDataCenterInfoEncodeDecodeWithJson() throws Exception {
        doMyDataCenterInfoEncodeDecodeTest(codec.getJsonMapper());
    }

    @Test
    public void testMyDataCenterInfoEncodeDecodeWithXml() throws Exception {
        doMyDataCenterInfoEncodeDecodeTest(codec.getXmlMapper());
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
        doLeaseInfoEncodeDecode(codec.getJsonMapper());
    }

    @Test
    public void testLeaseInfoEncodeDecodeWithXml() throws Exception {
        doLeaseInfoEncodeDecode(codec.getXmlMapper());
    }

    private void doLeaseInfoEncodeDecode(ObjectMapper mapper) throws Exception {
        LeaseInfo leaseInfo = infoIterator.next().getLeaseInfo();

        String encodedString = mapper.writeValueAsString(leaseInfo);
        LeaseInfo decodedValue = mapper.readValue(encodedString, LeaseInfo.class);
        assertThat(EurekaEntityComparators.equal(leaseInfo, decodedValue), is(true));
    }

    @Test
    public void testInstanceInfoEncodeDecodeWithJson() throws Exception {
        doInstanceInfoEncodeDecode(codec.getJsonMapper());
    }

    @Test
    public void testInstanceInfoEncodeDecodeWithXml() throws Exception {
        doInstanceInfoEncodeDecode(codec.getXmlMapper());
    }

    private void doInstanceInfoEncodeDecode(ObjectMapper mapper) throws Exception {
        InstanceInfo instanceInfo = infoIterator.next();

        String encodedString = mapper.writeValueAsString(instanceInfo);
        InstanceInfo decodedValue = mapper.readValue(encodedString, InstanceInfo.class);
        assertThat(EurekaEntityComparators.equal(instanceInfo, decodedValue), is(true));
    }

    @Test
    public void testInstanceInfoCompactEncodeDecodeWithJson() throws Exception {
        doInstanceInfoCompactEncodeDecode(compactCodec.getJsonMapper());
    }

    @Test
    public void testInstanceInfoCompactEncodeDecodeWithXml() throws Exception {
        doInstanceInfoCompactEncodeDecode(compactCodec.getXmlMapper());
    }

    private void doInstanceInfoCompactEncodeDecode(ObjectMapper mapper) throws Exception {
        InstanceInfo instanceInfo = infoIterator.next();

        String encodedString = mapper.writeValueAsString(instanceInfo);
        InstanceInfo decodedValue = mapper.readValue(encodedString, InstanceInfo.class);

        assertThat(decodedValue.getId(), is(equalTo(instanceInfo.getId())));
    }

    @Test
    public void testInstanceInfoIgnoredFieldsAreFilteredOutDuringDeserializationProcessWithJson() throws Exception {
        doInstanceInfoIgnoredFieldsAreFilteredOutDuringDeserializationProcess(codec.getJsonMapper(), compactCodec.getJsonMapper());
    }

    @Test
    public void testInstanceInfoIgnoredFieldsAreFilteredOutDuringDeserializationProcessWithXml() throws Exception {
        doInstanceInfoIgnoredFieldsAreFilteredOutDuringDeserializationProcess(codec.getXmlMapper(), compactCodec.getXmlMapper());
    }

    public void doInstanceInfoIgnoredFieldsAreFilteredOutDuringDeserializationProcess(ObjectMapper fullMapper, ObjectMapper compactMapper) throws Exception {
        InstanceInfo instanceInfo = infoIterator.next();

        // We use regular codec here to have all fields serialized
        String encodedString = fullMapper.writeValueAsString(instanceInfo);
        InstanceInfo decodedValue = compactMapper.readValue(encodedString, InstanceInfo.class);

        assertThat(decodedValue.getId(), is(equalTo(instanceInfo.getId())));
        assertThat(decodedValue.getAppName(), is(equalTo(instanceInfo.getAppName())));
        assertThat(decodedValue.getAppGroupName(), is(equalTo(instanceInfo.getAppGroupName())));
        assertThat(decodedValue.getIPAddr(), is(equalTo(instanceInfo.getIPAddr())));
        assertThat(decodedValue.getVIPAddress(), is(equalTo(instanceInfo.getVIPAddress())));
        assertThat(decodedValue.getSecureVipAddress(), is(equalTo(instanceInfo.getSecureVipAddress())));
        assertThat(decodedValue.getHostName(), is(equalTo(instanceInfo.getHostName())));
        assertThat(decodedValue.getStatus(), is(equalTo(instanceInfo.getStatus())));
        assertThat(decodedValue.getActionType(), is(equalTo(instanceInfo.getActionType())));
        assertThat(decodedValue.getASGName(), is(equalTo(instanceInfo.getASGName())));

        AmazonInfo sourceAmazonInfo = (AmazonInfo) instanceInfo.getDataCenterInfo();
        AmazonInfo decodedAmazonInfo = (AmazonInfo) decodedValue.getDataCenterInfo();
        assertThat(decodedAmazonInfo.get(MetaDataKey.accountId), is(equalTo(sourceAmazonInfo.get(MetaDataKey.accountId))));
    }

    @Test
    public void testApplicationEncodeDecodeWithJson() throws Exception {
        doApplicationEncodeDecode(codec.getJsonMapper());
    }

    @Test
    public void testApplicationEncodeDecodeWithXml() throws Exception {
        doApplicationEncodeDecode(codec.getXmlMapper());
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
        doApplicationsEncodeDecode(codec.getJsonMapper());
    }

    @Test
    public void testApplicationsEncodeDecodeWithXml() throws Exception {
        doApplicationsEncodeDecode(codec.getXmlMapper());
    }

    private void doApplicationsEncodeDecode(ObjectMapper mapper) throws Exception {
        Applications applications = infoGenerator.takeDelta(2);

        String encodedString = mapper.writeValueAsString(applications);
        Applications decodedValue = mapper.readValue(encodedString, Applications.class);
        assertThat(EurekaEntityComparators.equal(applications, decodedValue), is(true));
    }
}