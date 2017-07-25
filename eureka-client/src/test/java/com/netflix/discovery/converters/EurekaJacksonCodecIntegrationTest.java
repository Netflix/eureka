package com.netflix.discovery.converters;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Test;

import com.netflix.discovery.shared.Applications;

/**
 * this integration test parses the response of a Eureka discovery server,
 * specified by url via system property 'discovery.url'. It's useful for memory
 * utilization and performance tests, but since it's environment specific, the
 * tests below are @Ignore'd.
 *
 */
@org.junit.Ignore
public class EurekaJacksonCodecIntegrationTest {
    private static final int UNREASONABLE_TIMEOUT_MS = 500;
    private final EurekaJacksonCodec codec = new EurekaJacksonCodec("", "");

    /**
     * parse discovery response in a long-running loop with a delay
     * 
     * @throws Exception
     */
    @Test
    public void testRealDecode() throws Exception {
        Applications applications;
        File localDiscovery = new File("/var/folders/6j/qy6n1npj11x5j2j_9ng2wzmw0000gp/T/discovery-data-6054758555577530004.json"); //downloadRegistration(System.getProperty("discovery.url"));
        long testStart = System.currentTimeMillis();
        for (int i = 0; i < 60; i++) {
            try (InputStream is = new FileInputStream(localDiscovery)) {
                long start = System.currentTimeMillis();
                applications = codec.readValue(Applications.class, is);
                System.out.println("found some applications: " + applications.getRegisteredApplications().size()
                        + " et: " + (System.currentTimeMillis() - start));
            }
        }
        System.out.println("test time: " 
                + " et: " + (System.currentTimeMillis() - testStart));
    }
    
    
    @Test
    public void testCuriosity() {
        char[] arr1 = "test".toCharArray();
        char[] arr2 = new char[] {'t', 'e', 's', 't'};
        
        System.out.println("array equals" + arr1.equals(arr2));
    }

    /**
     * parse discovery response with an unreasonable timeout, so that the
     * parsing job is cancelled
     * 
     * @throws Exception
     */
    @Test
    public void testDecodeTimeout() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        File localDiscovery = downloadRegistration(System.getProperty("discovery.url"));
        Callable<Applications> task = () -> {
            try (InputStream is = new FileInputStream(localDiscovery)) {
                return codec.readValue(Applications.class, is);
            }
        };

        final int cancelAllButNthTask = 3;
        for (int i = 0; i < 30; i++) {
            Future<Applications> appsFuture = executor.submit(task);
            if (i % cancelAllButNthTask < cancelAllButNthTask - 1) {
                Thread.sleep(UNREASONABLE_TIMEOUT_MS);
                System.out.println("cancelling..." + " i: " + i + " - " + (i % 3));
                appsFuture.cancel(true);
            }
            try {
                Applications apps = appsFuture.get();
                System.out.println("found some applications: " + apps.toString() + ":"
                        + apps.getRegisteredApplications().size() + " i: " + i + " - " + (i % 3));
            } catch (Exception e) {
                System.out.println(e + " cause: " + " i: " + i + " - " + (i % 3));
            }
        }
    }

    /**
     * low-tech http downloader
     */
    private static File downloadRegistration(String discoveryUrl) throws IOException {
        if (discoveryUrl == null) {
            throw new IllegalArgumentException("null value not allowed for parameter discoveryUrl");
        }
        File localFile = File.createTempFile("discovery-data-", ".json");
        URL url = new URL(discoveryUrl);
        System.out.println("downloading registration data from " + url + " to " + localFile);
        HttpURLConnection hurlConn = (HttpURLConnection) url.openConnection();
        hurlConn.setDoOutput(true);
        hurlConn.setRequestProperty("accept", "application/json");
        hurlConn.connect();
        try (InputStream is = hurlConn.getInputStream()) {
            java.nio.file.Files.copy(is, localFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return localFile;

    }

}
