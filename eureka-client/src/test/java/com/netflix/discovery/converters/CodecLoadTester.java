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

/**
 * @author Tomasz Bak
 */
public class CodecLoadTester {

    private final List<InstanceInfo> instanceInfoList = new ArrayList<InstanceInfo>();
    private final List<Application> applicationList = new ArrayList<Application>();
    private final Applications applications = new Applications();

    private final EntityBodyConverter xstreamCodec = new EntityBodyConverter();
    private final EurekaJacksonCodec jacksonCodec = new EurekaJacksonCodec();

    public CodecLoadTester(int instanceCount, int appCount) {
        Iterator<InstanceInfo> instanceIt = new InstanceInfoGenerator(instanceCount, appCount, true).serviceIterator();
        int appIdx = 0;
        while (instanceIt.hasNext()) {
            InstanceInfo next = instanceIt.next();

            instanceInfoList.add(next);

            if (applicationList.size() >= appIdx) {
                applicationList.add(new Application(next.getAppName()));
            }
            applicationList.get(appIdx).addInstance(next);
            appIdx = (appIdx + 1) % appCount;
        }

        for (Application app : applicationList) {
            applications.addApplication(app);
        }
    }

    public void runApplicationsLoadTest(int loops, Action1<Applications> action) {
        for (int i = 0; i < loops; i++) {
            action.call(applications);
        }
    }

    public void runApplicationLoadTest(int loops, Action1<Application> action) {
        for (int i = 0; i < loops; i++) {
            action.call(applicationList.get(i % applicationList.size()));
        }
    }

    public void runInstanceInfoLoadTest(int loops, Action1<InstanceInfo> action) {
        for (int i = 0; i < loops; i++) {
            action.call(instanceInfoList.get(i % instanceInfoList.size()));
        }
    }

    public void runInstanceInfoIntervalTest(int batch, int intervalMs, long durationSec, Action1 action) {
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

    public void runApplicationIntervalTest(int batch, int intervalMs, long durationSec, Action1 action) {
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


    Action1 jacksonAction = new Action1<Object>() {
        @Override
        public void call(Object object) {
            ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
            try {
                jacksonCodec.writeTo(object, captureStream);
                byte[] bytes = captureStream.toByteArray();
                InputStream source = new ByteArrayInputStream(bytes);
                jacksonCodec.readFrom(object.getClass(), source);
            } catch (IOException e) {
                throw new RuntimeException("unexpected", e);
            }
        }
    };

    Action1 xstreamAction = new Action1<Object>() {
        @Override
        public void call(Object object) {
            ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
            try {
                xstreamCodec.write(object, captureStream, MediaType.APPLICATION_JSON_TYPE);
                byte[] bytes = captureStream.toByteArray();
                InputStream source = new ByteArrayInputStream(bytes);
                xstreamCodec.read(source, InstanceInfo.class, MediaType.APPLICATION_JSON_TYPE);
            } catch (IOException e) {
                throw new RuntimeException("unexpected", e);
            }
        }
    };

    public void runFullSpeed() {
        int loop = 100000;
        long start = System.currentTimeMillis();

        runInstanceInfoLoadTest(loop, jacksonAction);
//        runInstanceInfoLoadTest(loop, xstreamAction);

//        runApplicationLoadTest(loop, jacksonAction);
//        runApplicationLoadTest(loop, xstreamAction);

//        runApplicationsLoadTest(loop, jacksonAction);
//        runApplicationsLoadTest(loop, xstreamAction);

        long executionTime = System.currentTimeMillis() - start;
        System.out.println("Execution time: " + executionTime);
    }

    public void runIntervals() {
        int batch = 1500;
        int intervalMs = 1000;
        long durationSec = 600;

//        runInstanceInfoIntervalTest(batch, intervalMs, durationSec, jacksonAction);
        runInstanceInfoIntervalTest(batch, intervalMs, durationSec, xstreamAction);

//        runApplicationIntervalTest(batch, intervalMs, durationSec, jacksonAction);
//        runApplicationIntervalTest(batch, intervalMs, durationSec, xstreamAction);
    }


    public static void main(String[] args) {
//        new CodecLoadTester(10000, 100).runFullSpeed();
        new CodecLoadTester(10000, 100).runIntervals();
    }

    interface Action1<T> {
        void call(T data);
    }
}
