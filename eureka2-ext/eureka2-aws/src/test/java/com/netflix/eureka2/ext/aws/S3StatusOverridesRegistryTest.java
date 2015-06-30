package com.netflix.eureka2.ext.aws;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class S3StatusOverridesRegistryTest {

    private static final long REFRESH_INTERVAL_SEC = 30;

    private static final InstanceInfo instanceInfo1 = SampleInstanceInfo.CliServer.build();
    private static final InstanceInfo instanceInfo2 = SampleInstanceInfo.ZuulServer.build();
    private static final InstanceInfo instanceInfo3 = SampleInstanceInfo.WebServer.build();

    private final TestScheduler testScheduler = Schedulers.test();

    private final AmazonS3Client amazonS3Client = mock(AmazonS3Client.class);
    private final S3OverridesConfiguration config = mock(S3OverridesConfiguration.class);

    private final S3StatusOverridesRegistry registry = new S3StatusOverridesRegistry(
            amazonS3Client,
            config,
            testScheduler
    );

    private final MockS3Service mockS3Service = new MockS3Service();

    @Before
    public void setUp() throws Exception {
        when(config.getRefreshIntervalSec()).thenReturn(REFRESH_INTERVAL_SEC);
        when(config.getBucketName()).thenReturn("myBucketName");
        when(config.getPrefix()).thenReturn("eureka2.overrides.test");

        mockS3Service.setupMocks();

        registry.start();
    }

    @After
    public void tearDown() throws Exception {
        registry.stop();
    }

    @Test
    public void testReturnsFalseIfNoDataProvidedFromAWS() throws Exception {
        ExtTestSubscriber<Boolean> testSubscriber = new ExtTestSubscriber<>();
        registry.shouldApplyOutOfService(instanceInfo1).subscribe(testSubscriber);

        testScheduler.triggerActions();
        assertThat(testSubscriber.takeNext(), is(false));
    }

    @Test
    public void testPutAndRemoveFromS3() throws Exception {
        registry.setOutOfService(instanceInfo1).subscribe();
        assertThat(mockS3Service.names.size(), is(1));
        assertThat(mockS3Service.names.keySet(), containsInAnyOrder(
                registry.toS3Name(config.getPrefix(), instanceInfo1.getId())
        ));

        registry.setOutOfService(instanceInfo2).subscribe();
        assertThat(mockS3Service.names.size(), is(2));
        assertThat(mockS3Service.names.keySet(), containsInAnyOrder(
                registry.toS3Name(config.getPrefix(), instanceInfo1.getId()),
                registry.toS3Name(config.getPrefix(), instanceInfo2.getId())
        ));

        registry.unsetOutOfService(instanceInfo1).subscribe();
        assertThat(mockS3Service.names.size(), is(1));
        assertThat(mockS3Service.names.keySet(), containsInAnyOrder(
                registry.toS3Name(config.getPrefix(), instanceInfo2.getId())
        ));
    }

    @Test
    public void testShouldApplyOverrideIfFilesExistInS3() throws Exception {
        registry.setOutOfService(instanceInfo1).subscribe();
        registry.setOutOfService(instanceInfo2).subscribe();

        ExtTestSubscriber<Boolean> testSubscriber1 = new ExtTestSubscriber<>();
        registry.shouldApplyOutOfService(instanceInfo1).subscribe(testSubscriber1);

        ExtTestSubscriber<Boolean> testSubscriber2 = new ExtTestSubscriber<>();
        registry.shouldApplyOutOfService(instanceInfo2).subscribe(testSubscriber2);

        assertThat(testSubscriber1.takeNext(), is(false));
        assertThat(testSubscriber2.takeNext(), is(false));

        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        assertThat(testSubscriber1.takeNext(), is(true));
        assertThat(testSubscriber2.takeNext(), is(true));

        ExtTestSubscriber<Boolean> testSubscriber3 = new ExtTestSubscriber<>();
        registry.shouldApplyOutOfService(instanceInfo3).subscribe(testSubscriber3);
        assertThat(testSubscriber3.takeNext(), is(false));

        registry.setOutOfService(instanceInfo3).subscribe();
        registry.unsetOutOfService(instanceInfo1).subscribe();

        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        assertThat(testSubscriber1.takeNext(), is(false));
        assertThat(testSubscriber2.takeNext(), is(nullValue()));  // no change so no emit
        assertThat(testSubscriber3.takeNext(), is(true));
    }


    class MockS3Service {
        ConcurrentMap<String, Boolean> names = new ConcurrentHashMap<>();

        public void put(String name) {
            names.put(name, true);
        }

        public void remove(String name) {
            names.remove(name);
        }

        public ObjectListing getListing() {
            ObjectListing listing = mock(ObjectListing.class);
            ArrayList<S3ObjectSummary> summaries = new ArrayList<>();
            for (String name : names.keySet()) {
                S3ObjectSummary summary = new S3ObjectSummary();
                summary.setKey(name);
                summaries.add(summary);
            }

            when(listing.getObjectSummaries()).thenReturn(summaries);
            when(listing.isTruncated()).thenReturn(false);
            when(listing.getNextMarker()).thenReturn(null);
            return listing;
        }

        public void setupMocks() {
            doAnswer(new Answer() {
                @Override
                public Object answer(InvocationOnMock invocation) throws Throwable {
                    String name = invocation.getArguments()[1].toString();
                    remove(name);
                    return null;
                }
            }).when(amazonS3Client).deleteObject(anyString(), anyString());

            when(amazonS3Client.putObject(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                    .thenAnswer(new Answer<PutObjectResult>() {
                        @Override
                        public PutObjectResult answer(InvocationOnMock invocation) throws Throwable {
                            String name = invocation.getArguments()[1].toString();
                            put(name);
                            return new PutObjectResult();
                        }
                    });

            when(amazonS3Client.listObjects(any(ListObjectsRequest.class))).thenAnswer(new Answer<ObjectListing>() {
                @Override
                public ObjectListing answer(InvocationOnMock invocation) throws Throwable {
                    ObjectListing listing = getListing();
                    return listing;
                }
            });
        }
    }
}
