package com.netflix.discovery.converters;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Iterator;

import javax.ws.rs.core.MediaType;

import org.junit.Test;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.ActionType;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.EurekaEntityComparators;
import com.netflix.discovery.util.InstanceInfoGenerator;

/**
 * @author Tomasz Bak
 */
public class EurekaJacksonCodecTest {

    public static final InstanceInfo INSTANCE_INFO_1_A1;
    public static final InstanceInfo INSTANCE_INFO_2_A1;
    public static final InstanceInfo INSTANCE_INFO_1_A2;
    public static final InstanceInfo INSTANCE_INFO_2_A2;
    public static final Application APPLICATION_1;
    public static final Application APPLICATION_2;
    public static final Applications APPLICATIONS;

    static {
        Iterator<InstanceInfo> infoIterator = InstanceInfoGenerator.newBuilder(4, 2).withMetaData(true).build().serviceIterator();

        INSTANCE_INFO_1_A1 = infoIterator.next();
        INSTANCE_INFO_1_A1.setActionType(ActionType.ADDED);
        INSTANCE_INFO_1_A2 = infoIterator.next();
        INSTANCE_INFO_1_A2.setActionType(ActionType.ADDED);
        INSTANCE_INFO_2_A1 = infoIterator.next();
        INSTANCE_INFO_1_A2.setActionType(ActionType.ADDED);
        INSTANCE_INFO_2_A2 = infoIterator.next();
        INSTANCE_INFO_2_A2.setActionType(ActionType.ADDED);

        APPLICATION_1 = new Application(INSTANCE_INFO_1_A1.getAppName());
        APPLICATION_1.addInstance(INSTANCE_INFO_1_A1);
        APPLICATION_1.addInstance(INSTANCE_INFO_2_A1);

        APPLICATION_2 = new Application(INSTANCE_INFO_1_A2.getAppName());
        APPLICATION_2.addInstance(INSTANCE_INFO_1_A2);
        APPLICATION_2.addInstance(INSTANCE_INFO_2_A2);

        APPLICATIONS = new Applications();
        APPLICATIONS.addApplication(APPLICATION_1);
        APPLICATIONS.addApplication(APPLICATION_2);
    }

    private final EurekaJacksonCodec codec = new EurekaJacksonCodec();

    @Test
    public void testInstanceInfoJacksonEncodeDecode() throws Exception {
        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        codec.writeTo(INSTANCE_INFO_1_A1, captureStream);
        byte[] encoded = captureStream.toByteArray();

        // Decode
        InputStream source = new ByteArrayInputStream(encoded);
        InstanceInfo decoded = codec.readValue(InstanceInfo.class, source);

        assertTrue(EurekaEntityComparators.equal(decoded, INSTANCE_INFO_1_A1));
    }

    @Test
    public void testInstanceInfoJacksonEncodeDecodeWithoutMetaData() throws Exception {
        InstanceInfo noMetaDataInfo = InstanceInfoGenerator.newBuilder(1, 1).withMetaData(false).build().serviceIterator().next();

        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        codec.writeTo(noMetaDataInfo, captureStream);
        byte[] encoded = captureStream.toByteArray();

        // Decode
        InputStream source = new ByteArrayInputStream(encoded);
        InstanceInfo decoded = codec.readValue(InstanceInfo.class, source);

        assertTrue(EurekaEntityComparators.equal(decoded, noMetaDataInfo));
    }

    @Test
    public void testInstanceInfoXStreamEncodeJacksonDecode() throws Exception {
        InstanceInfo original = INSTANCE_INFO_1_A1;

        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        new EntityBodyConverter().write(original, captureStream, MediaType.APPLICATION_JSON_TYPE);
        byte[] encoded = captureStream.toByteArray();

        // Decode
        InputStream source = new ByteArrayInputStream(encoded);
        InstanceInfo decoded = codec.readValue(InstanceInfo.class, source);

        assertTrue(EurekaEntityComparators.equal(decoded, original));
    }

    @Test
    public void testInstanceInfoJacksonEncodeXStreamDecode() throws Exception {
        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        codec.writeTo(INSTANCE_INFO_1_A1, captureStream);
        byte[] encoded = captureStream.toByteArray();

        // Decode
        InputStream source = new ByteArrayInputStream(encoded);
        InstanceInfo decoded = (InstanceInfo) new EntityBodyConverter().read(source, InstanceInfo.class, MediaType.APPLICATION_JSON_TYPE);

        assertTrue(EurekaEntityComparators.equal(decoded, INSTANCE_INFO_1_A1));
    }

    @Test
    public void testApplicationJacksonEncodeDecode() throws Exception {
        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        codec.writeTo(APPLICATION_1, captureStream);
        byte[] encoded = captureStream.toByteArray();

        // Decode
        InputStream source = new ByteArrayInputStream(encoded);
        Application decoded = codec.readValue(Application.class, source);

        assertTrue(EurekaEntityComparators.equal(decoded, APPLICATION_1));
    }

    @Test
    public void testApplicationXStreamEncodeJacksonDecode() throws Exception {
        Application original = APPLICATION_1;

        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        new EntityBodyConverter().write(original, captureStream, MediaType.APPLICATION_JSON_TYPE);
        byte[] encoded = captureStream.toByteArray();

        // Decode
        InputStream source = new ByteArrayInputStream(encoded);
        Application decoded = codec.readValue(Application.class, source);

        assertTrue(EurekaEntityComparators.equal(decoded, original));
    }

    @Test
    public void testApplicationJacksonEncodeXStreamDecode() throws Exception {
        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        codec.writeTo(APPLICATION_1, captureStream);
        byte[] encoded = captureStream.toByteArray();

        // Decode
        InputStream source = new ByteArrayInputStream(encoded);
        Application decoded = (Application) new EntityBodyConverter().read(source, Application.class, MediaType.APPLICATION_JSON_TYPE);

        assertTrue(EurekaEntityComparators.equal(decoded, APPLICATION_1));
    }

    @Test
    public void testApplicationsJacksonEncodeDecode() throws Exception {
        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        codec.writeTo(APPLICATIONS, captureStream);
        byte[] encoded = captureStream.toByteArray();

        // Decode
        InputStream source = new ByteArrayInputStream(encoded);
        Applications decoded = codec.readValue(Applications.class, source);

        assertTrue(EurekaEntityComparators.equal(decoded, APPLICATIONS));
    }

    @Test
    public void testApplicationsXStreamEncodeJacksonDecode() throws Exception {
        Applications original = APPLICATIONS;

        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        new EntityBodyConverter().write(original, captureStream, MediaType.APPLICATION_JSON_TYPE);
        byte[] encoded = captureStream.toByteArray();
        String encodedString = new String(encoded);
        // Decode
        InputStream source = new ByteArrayInputStream(encoded);
        Applications decoded = codec.readValue(Applications.class, source);

        assertTrue(EurekaEntityComparators.equal(decoded, original));
    }

    @Test
    public void testApplicationsJacksonEncodeXStreamDecode() throws Exception {
        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        codec.writeTo(APPLICATIONS, captureStream);
        byte[] encoded = captureStream.toByteArray();

        // Decode
        InputStream source = new ByteArrayInputStream(encoded);
        Applications decoded = (Applications) new EntityBodyConverter().read(source, Applications.class, MediaType.APPLICATION_JSON_TYPE);

        assertTrue(EurekaEntityComparators.equal(decoded, APPLICATIONS));
    }

    @Test
    public void testJacksonWriteToString() throws Exception {
        String jsonValue = codec.writeToString(INSTANCE_INFO_1_A1);
        InstanceInfo decoded = codec.readValue(InstanceInfo.class, new ByteArrayInputStream(jsonValue.getBytes(Charset.defaultCharset())));

        assertTrue(EurekaEntityComparators.equal(decoded, INSTANCE_INFO_1_A1));
    }

    @Test
    public void testJacksonWrite() throws Exception {
        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        codec.writeTo(INSTANCE_INFO_1_A1, captureStream);
        byte[] encoded = captureStream.toByteArray();

        // Decode value
        InputStream source = new ByteArrayInputStream(encoded);
        InstanceInfo decoded = codec.readValue(InstanceInfo.class, source);

        assertTrue(EurekaEntityComparators.equal(decoded, INSTANCE_INFO_1_A1));
    }
    
}