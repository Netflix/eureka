package com.netflix.discovery.converters;

import javax.ws.rs.core.MediaType;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.jackson.AbstractEurekaJacksonCodec;
import com.netflix.discovery.converters.jackson.EurekaJsonJacksonCodec;
import com.netflix.discovery.converters.jackson.EurekaXmlJacksonCodec;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.InstanceInfoGenerator;

/**
 * @author Tomasz Bak
 */
public class CodecLoadTester {

    private final List<InstanceInfo> instanceInfoList = new ArrayList<InstanceInfo>();
    private final List<Application> applicationList = new ArrayList<Application>();
    private final Applications applications;

    private final EntityBodyConverter xstreamCodec = new EntityBodyConverter();
    private final EurekaJacksonCodec legacyJacksonCodec = new EurekaJacksonCodec();

    private final EurekaJsonJacksonCodec jsonCodecNG = new EurekaJsonJacksonCodec();
    private final EurekaJsonJacksonCodec jsonCodecNgCompact = new EurekaJsonJacksonCodec(KeyFormatter.defaultKeyFormatter(), true);

    private final EurekaXmlJacksonCodec xmlCodecNG = new EurekaXmlJacksonCodec();
    private final EurekaXmlJacksonCodec xmlCodecNgCompact = new EurekaXmlJacksonCodec(KeyFormatter.defaultKeyFormatter(), true);

    static class FirstHolder {
        Applications value;
    }

    static class SecondHolder {
        Applications value;
    }

    private FirstHolder firstHolder = new FirstHolder();
    private SecondHolder secondHolder = new SecondHolder();

    public CodecLoadTester(int instanceCount, int appCount) {
        Iterator<InstanceInfo> instanceIt = InstanceInfoGenerator.newBuilder(instanceCount, appCount)
                .withMetaData(true).build().serviceIterator();
        applications = new Applications();
        int appIdx = 0;
        while (instanceIt.hasNext()) {
            InstanceInfo next = instanceIt.next();

            instanceInfoList.add(next);

            if (applicationList.size() <= appIdx) {
                applicationList.add(new Application(next.getAppName()));
            }
            applicationList.get(appIdx).addInstance(next);
            appIdx = (appIdx + 1) % appCount;
        }

        for (Application app : applicationList) {
            applications.addApplication(app);
        }
        applications.setAppsHashCode(applications.getReconcileHashCode());
        firstHolder.value = applications;
    }

    public CodecLoadTester(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("ERROR: too many command line arguments; file name expected only");
            throw new IllegalArgumentException();
        }
        String fileName = args[0];
        Applications applications;
        try {
            System.out.println("Attempting to load " + fileName + " in XML format...");
            applications = loadWithCodec(fileName, MediaType.APPLICATION_XML_TYPE);
        } catch (Exception e) {
            System.out.println("Attempting to load " + fileName + " in JSON format...");
            applications = loadWithCodec(fileName, MediaType.APPLICATION_JSON_TYPE);
        }
        this.applications = applications;

        long totalInstances = 0;
        for (Application a : applications.getRegisteredApplications()) {
            totalInstances += a.getInstances().size();
        }
        System.out.printf("Loaded %d applications with %d instances\n", applications.getRegisteredApplications().size(), totalInstances);
        firstHolder.value = applications;
    }

    private Applications loadWithCodec(String fileName, MediaType mediaType) throws IOException {
        FileInputStream fis = new FileInputStream(fileName);
        BufferedInputStream bis = new BufferedInputStream(fis);
        return (Applications) xstreamCodec.read(bis, Applications.class, mediaType);
    }

    public void runApplicationsLoadTest(int loops, Func0<Applications> action) {
        long size = 0;
        for (int i = 0; i < loops; i++) {
            size += action.call(applications);
        }
        System.out.println("Average applications object size=" + formatSize(size / loops));
    }

    public void runApplicationLoadTest(int loops, Func0<Application> action) {
        for (int i = 0; i < loops; i++) {
            action.call(applicationList.get(i % applicationList.size()));
        }
    }

    public void runInstanceInfoLoadTest(int loops, Func0<InstanceInfo> action) {
        for (int i = 0; i < loops; i++) {
            action.call(instanceInfoList.get(i % instanceInfoList.size()));
        }
    }

    public void runInstanceInfoIntervalTest(int batch, int intervalMs, long durationSec, Func0 action) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + durationSec * 1000;
        long now;
        do {
            now = System.currentTimeMillis();
            runInstanceInfoLoadTest(batch, action);
            long waiting = intervalMs - (System.currentTimeMillis() - now);
            System.out.println("Waiting " + waiting + "ms");
            if (waiting > 0) {
                try {
                    Thread.sleep(waiting);
                } catch (InterruptedException e) {
                    // IGNORE
                }
            }
        } while (now < endTime);
    }

    public void runApplicationIntervalTest(int batch, int intervalMs, long durationSec, Func0 action) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + durationSec * 1000;
        long now;
        do {
            now = System.currentTimeMillis();
            runApplicationLoadTest(batch, action);
            long waiting = intervalMs - (System.currentTimeMillis() - now);
            System.out.println("Waiting " + waiting + "ms");
            if (waiting > 0) {
                try {
                    Thread.sleep(waiting);
                } catch (InterruptedException e) {
                    // IGNORE
                }
            }
        } while (now < endTime);
    }

    private static String formatSize(long size) {
        if (size < 1000) {
            return String.format("%d [bytes]", size);
        }
        if (size < 1024 * 1024) {
            return String.format("%.2f [KB]", size / 1024f);
        }
        return String.format("%.2f [MB]", size / (1024f * 1024f));
    }

    interface Func0<T> {
        int call(T data);
    }

    Func0 legacyJacksonAction = new Func0<Object>() {
        @Override
        public int call(Object object) {
            ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
            try {
                legacyJacksonCodec.writeTo(object, captureStream);
                byte[] bytes = captureStream.toByteArray();
                InputStream source = new ByteArrayInputStream(bytes);
                legacyJacksonCodec.readValue(object.getClass(), source);

                return bytes.length;
            } catch (IOException e) {
                throw new RuntimeException("unexpected", e);
            }
        }
    };

    Func0 xstreamJsonAction = new Func0<Object>() {
        @Override
        public int call(Object object) {
            ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
            try {
                xstreamCodec.write(object, captureStream, MediaType.APPLICATION_JSON_TYPE);
                byte[] bytes = captureStream.toByteArray();
                InputStream source = new ByteArrayInputStream(bytes);
                xstreamCodec.read(source, InstanceInfo.class, MediaType.APPLICATION_JSON_TYPE);

                return bytes.length;
            } catch (IOException e) {
                throw new RuntimeException("unexpected", e);
            }
        }
    };

    Func0 xstreamXmlAction = new Func0<Object>() {
        @Override
        public int call(Object object) {
            ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
            try {
                xstreamCodec.write(object, captureStream, MediaType.APPLICATION_XML_TYPE);
                byte[] bytes = captureStream.toByteArray();
                InputStream source = new ByteArrayInputStream(bytes);
                xstreamCodec.read(source, InstanceInfo.class, MediaType.APPLICATION_XML_TYPE);

                return bytes.length;
            } catch (IOException e) {
                throw new RuntimeException("unexpected", e);
            }
        }
    };

    Func0 createJacksonNgAction(final MediaType mediaType, final boolean compact) {
        return new Func0<Object>() {
            @Override
            public int call(Object object) {
                AbstractEurekaJacksonCodec codec;
                if (mediaType.equals(MediaType.APPLICATION_JSON_TYPE)) {
                    codec = compact ? jsonCodecNgCompact : jsonCodecNG;
                } else {
                    codec = compact ? xmlCodecNgCompact : xmlCodecNG;
                }
                ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
                try {
                    codec.writeTo(object, captureStream);
                    byte[] bytes = captureStream.toByteArray();
                    InputStream source = new ByteArrayInputStream(bytes);
                    Applications readValue = codec.getObjectMapper(object.getClass()).readValue(source, Applications.class);
                    secondHolder.value = readValue;

                    return bytes.length;
                } catch (IOException e) {
                    throw new RuntimeException("unexpected", e);
                }
            }
        };
    }

    public void runFullSpeed() {
        int loop = 5;
        System.gc();
        long start = System.currentTimeMillis();

//        runInstanceInfoLoadTest(loop, legacyJacksonAction);
//        runInstanceInfoLoadTest(loop, xstreamAction);

//        runApplicationLoadTest(loop, legacyJacksonAction);
//        runApplicationLoadTest(loop, xstreamAction);

        // ----------------------------------------------------------------
        // Applications
//        runApplicationsLoadTest(loop, xstreamJsonAction);
//        runApplicationsLoadTest(loop, xstreamXmlAction);

//        runApplicationsLoadTest(loop, legacyJacksonAction);

        runApplicationsLoadTest(loop, createJacksonNgAction(MediaType.APPLICATION_JSON_TYPE, false));

        long executionTime = System.currentTimeMillis() - start;
        System.out.printf("Execution time: %d[ms]\n", executionTime);
    }

    public void runIntervals() {
        int batch = 1500;
        int intervalMs = 1000;
        long durationSec = 600;

//        runInstanceInfoIntervalTest(batch, intervalMs, durationSec, legacyJacksonAction);
        runInstanceInfoIntervalTest(batch, intervalMs, durationSec, xstreamJsonAction);

//        runApplicationIntervalTest(batch, intervalMs, durationSec, legacyJacksonAction);
//        runApplicationIntervalTest(batch, intervalMs, durationSec, xstreamAction);
    }


    public static void main(String[] args) throws Exception {
        CodecLoadTester loadTester;
        if (args.length == 0) {
            loadTester = new CodecLoadTester(2000, 40);
        } else {
            loadTester = new CodecLoadTester(args);
        }
        loadTester.runFullSpeed();
        Thread.sleep(100000);
    }
}
