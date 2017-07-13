package com.netflix.discovery.converters;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.StreamSupport;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.ActionType;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.EurekaEntityComparators;
import com.netflix.discovery.util.InstanceInfoGenerator;

import org.junit.Ignore;
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
    
    @Test
    public void testRealDecode() throws Exception {
        Applications applications;
        File localDiscovery = downloadRegistration(System.getProperty("discovery.url"));
        for (int i=0; i < 30; i++) {
            try (InputStream is = new FileInputStream(localDiscovery)) {
                long start = System.currentTimeMillis();
                applications = codec.readValue(Applications.class, is);
                System.out.println("found some applications: " + applications.getRegisteredApplications().size() + " et: " + (System.currentTimeMillis() - start));
            }
            Thread.sleep(1000);
        }
    }
    
    @Test
    public void testDecodeTimeout() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        File localDiscovery = downloadRegistration(System.getProperty("discovery.url"));
        Callable<Applications> task = ()-> {
            try (InputStream is = new FileInputStream(localDiscovery)) {
                return codec.readValue(Applications.class, is);
            }
        };
        
        for (int i=0; i < 30; i++) {
            Future<Applications> appsFuture = executor.submit(task);
            if (i%3 < 2) {
                Thread.sleep(500);
                System.out.println("cancelling..." + " i: " + i + " - " + (i%3));
                appsFuture.cancel(true);
            }
            try {
                Applications apps = appsFuture.get();
               System.out.println("found some applications: " + apps.toString() + ":" + apps.getRegisteredApplications().size() + " i: " + i + " - " + (i%3));
            }
            catch (Exception e) {
                System.out.println(e + " cause: " + " i: " + i + " - " + (i%3));
            }
        }
    }
    
    private static File downloadRegistration(String discoveryUrl) throws IOException{
        if (discoveryUrl == null) {
            throw new IllegalArgumentException("null value not allowed for parameter discoveryUrl");
        } 
       File localFile = File.createTempFile("discovery-data-", ".json");
        URL url = new URL(discoveryUrl);
        System.out.println("downloading registration data from " + url + " to " + localFile);
        HttpURLConnection hurlConn = (HttpURLConnection)url.openConnection();
        hurlConn.setDoOutput(true);
        hurlConn.setRequestProperty("accept", "application/json");
        hurlConn.connect();
        try (InputStream is = hurlConn.getInputStream()) {
            java.nio.file.Files.copy(is, localFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return localFile;
        
    }
}