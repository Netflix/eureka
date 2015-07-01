package com.netflix.discovery.converters;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.InstanceInfoGenerator;

/**
 * @author Tomasz Bak
 */
public class CodecLoadTester {

    private final List<InstanceInfo> instanceInfoList = new ArrayList<InstanceInfo>();
    private final List<Application> applicationList = new ArrayList<Application>();
    private final Applications applications = new Applications();

    private final EntityBodyConverter xstreamCodec = new EntityBodyConverter();
    private final EurekaJacksonCodec jacksonCodec = new EurekaJacksonCodec();
    private final EurekaJacksonCodecNG jacksonCodecNG = new EurekaJacksonCodecNG();
    private final EurekaJacksonCodecNG jacksonCodecNgCompact = new EurekaJacksonCodecNG(true);

    public CodecLoadTester(int instanceCount, int appCount) {
        Iterator<InstanceInfo> instanceIt = InstanceInfoGenerator.newBuilder(instanceCount, appCount)
                .withMetaData(true).build().serviceIterator();
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


    Func0 jacksonAction = new Func0<Object>() {
        @Override
        public int call(Object object) {
            ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
            try {
                jacksonCodec.writeTo(object, captureStream);
                byte[] bytes = captureStream.toByteArray();
                InputStream source = new ByteArrayInputStream(bytes);
                jacksonCodec.readValue(object.getClass(), source);

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
                EurekaJacksonCodecNG codec = compact ? jacksonCodecNgCompact : jacksonCodecNG;
                ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
                try {
                    codec.writeTo(object, captureStream, mediaType);
                    byte[] bytes = captureStream.toByteArray();
                    InputStream source = new ByteArrayInputStream(bytes);
                    codec.readValue(object.getClass(), source, mediaType);

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

//        runInstanceInfoLoadTest(loop, jacksonAction);
//        runInstanceInfoLoadTest(loop, xstreamAction);

//        runApplicationLoadTest(loop, jacksonAction);
//        runApplicationLoadTest(loop, xstreamAction);

        // ----------------------------------------------------------------
        // Applications
//        runApplicationsLoadTest(loop, xstreamJsonAction);
//        runApplicationsLoadTest(loop, xstreamXmlAction);

//                runApplicationsLoadTest(loop, jacksonAction);

        runApplicationsLoadTest(loop, createJacksonNgAction(MediaType.APPLICATION_XML_TYPE, true));

        long executionTime = System.currentTimeMillis() - start;
        System.out.println("Execution time: " + executionTime);
    }

    public void runIntervals() {
        int batch = 1500;
        int intervalMs = 1000;
        long durationSec = 600;

//        runInstanceInfoIntervalTest(batch, intervalMs, durationSec, jacksonAction);
        runInstanceInfoIntervalTest(batch, intervalMs, durationSec, xstreamJsonAction);

//        runApplicationIntervalTest(batch, intervalMs, durationSec, jacksonAction);
//        runApplicationIntervalTest(batch, intervalMs, durationSec, xstreamAction);
    }


    public static void main(String[] args) throws IOException {
        CodecLoadTester loadTester = new CodecLoadTester(20000, 400);
        loadTester.runFullSpeed();
//        new CodecLoadTester(10000, 100).runIntervals();
    }

    interface Func0<T> {
        int call(T data);
    }
}
