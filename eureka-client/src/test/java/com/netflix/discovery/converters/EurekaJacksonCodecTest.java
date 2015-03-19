package com.netflix.discovery.converters;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Iterator;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.envelope.ApplicationEnvelope;
import com.netflix.discovery.converters.envelope.ApplicationsEnvelope;
import com.netflix.discovery.converters.envelope.InstanceInfoEnvelope;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

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
        Iterator<InstanceInfo> infoIterator = new InstanceInfoGenerator(4, 2).serviceIterator();
        INSTANCE_INFO_1_A1 = infoIterator.next();
        INSTANCE_INFO_1_A2 = infoIterator.next();
        INSTANCE_INFO_2_A1 = infoIterator.next();
        INSTANCE_INFO_2_A2 = infoIterator.next();

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
        InstanceInfoEnvelope original = new InstanceInfoEnvelope(INSTANCE_INFO_1_A1);

        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        codec.writeTo(original, captureStream);
        byte[] encoded = captureStream.toByteArray();

        // Decode
        InputStream source = new ByteArrayInputStream(encoded);
        InstanceInfoEnvelope decoded = codec.readFrom(InstanceInfoEnvelope.class, source);

        assertTrue(EurekaEntityComparators.equal(decoded.getInstance(), original.getInstance()));
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
        InstanceInfoEnvelope decoded = codec.readFrom(InstanceInfoEnvelope.class, source);

        assertTrue(EurekaEntityComparators.equal(decoded.getInstance(), original));
    }

    @Test
    public void testInstanceInfoJacksonEncodeXStreamDecode() throws Exception {
        InstanceInfoEnvelope original = new InstanceInfoEnvelope(INSTANCE_INFO_1_A1);

        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        codec.writeTo(original, captureStream);
        byte[] encoded = captureStream.toByteArray();

        // Decode
        InputStream source = new ByteArrayInputStream(encoded);
        InstanceInfo decoded = (InstanceInfo) new EntityBodyConverter().read(source, InstanceInfo.class, MediaType.APPLICATION_JSON_TYPE);

        assertTrue(EurekaEntityComparators.equal(decoded, original.getInstance()));
    }

    @Test
    public void testApplicationJacksonEncodeDecode() throws Exception {
        ApplicationEnvelope original = new ApplicationEnvelope(APPLICATION_1);

        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        codec.writeTo(original, captureStream);
        byte[] encoded = captureStream.toByteArray();

        // Decode
        InputStream source = new ByteArrayInputStream(encoded);
        ApplicationEnvelope decoded = codec.readFrom(ApplicationEnvelope.class, source);

        assertTrue(EurekaEntityComparators.equal(decoded.getApplication(), original.getApplication()));
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
        ApplicationEnvelope decoded = codec.readFrom(ApplicationEnvelope.class, source);

        assertTrue(EurekaEntityComparators.equal(decoded.getApplication(), original));
    }

    @Test
    public void testApplicationJacksonEncodeXStreamDecode() throws Exception {
        ApplicationEnvelope original = new ApplicationEnvelope(APPLICATION_1);

        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        codec.writeTo(original, captureStream);
        byte[] encoded = captureStream.toByteArray();

        // Decode
        InputStream source = new ByteArrayInputStream(encoded);
        Application decoded = (Application) new EntityBodyConverter().read(source, Application.class, MediaType.APPLICATION_JSON_TYPE);

        assertTrue(EurekaEntityComparators.equal(decoded, original.getApplication()));
    }

    @Test
    public void testApplicationsJacksonEncodeDecode() throws Exception {
        ApplicationsEnvelope original = new ApplicationsEnvelope(APPLICATIONS);

        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        codec.writeTo(original, captureStream);
        byte[] encoded = captureStream.toByteArray();

        // Decode
        InputStream source = new ByteArrayInputStream(encoded);
        ApplicationsEnvelope decoded = codec.readFrom(ApplicationsEnvelope.class, source);

        assertTrue(EurekaEntityComparators.equal(decoded.getApplications(), original.getApplications()));
    }

    @Test
    public void testApplicationsXStreamEncodeJacksonDecode() throws Exception {
        Applications original = APPLICATIONS;

        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        new EntityBodyConverter().write(original, captureStream, MediaType.APPLICATION_JSON_TYPE);
        byte[] encoded = captureStream.toByteArray();

        // Decode
        InputStream source = new ByteArrayInputStream(encoded);
        ApplicationsEnvelope decoded = codec.readFrom(ApplicationsEnvelope.class, source);

        assertTrue(EurekaEntityComparators.equal(decoded.getApplications(), original));
    }

    @Test
    public void testApplicationsJacksonEncodeXStreamDecode() throws Exception {
        ApplicationsEnvelope original = new ApplicationsEnvelope(APPLICATIONS);

        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        codec.writeTo(original, captureStream);
        byte[] encoded = captureStream.toByteArray();

        // Decode
        InputStream source = new ByteArrayInputStream(encoded);
        Applications decoded = (Applications) new EntityBodyConverter().read(source, Applications.class, MediaType.APPLICATION_JSON_TYPE);

        assertTrue(EurekaEntityComparators.equal(decoded, original.getApplications()));
    }

    @Test
    public void testJacksonWriteToString() throws Exception {
        String jsonValue = codec.writeToString(INSTANCE_INFO_1_A1);
        InstanceInfo decoded = codec.readFrom(InstanceInfo.class, new ByteArrayInputStream(jsonValue.getBytes(Charset.defaultCharset())));

        assertTrue(EurekaEntityComparators.equal(decoded, INSTANCE_INFO_1_A1));
    }

    @Test
    public void testJacksonWriteWithEnvelope() throws Exception {
        // Encode
        ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
        codec.writeWithEnvelopeTo(INSTANCE_INFO_1_A1, captureStream);
        byte[] encoded = captureStream.toByteArray();

        // Decode value wrapped in envelope
        InputStream source = new ByteArrayInputStream(encoded);
        InstanceInfoEnvelope decoded = codec.readFrom(InstanceInfoEnvelope.class, source);

        assertTrue(EurekaEntityComparators.equal(decoded.getInstance(), INSTANCE_INFO_1_A1));
    }
}