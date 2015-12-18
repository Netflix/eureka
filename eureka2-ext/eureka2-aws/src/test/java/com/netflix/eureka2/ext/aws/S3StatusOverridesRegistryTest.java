package com.netflix.eureka2.ext.aws;

import java.util.concurrent.TimeUnit;

import com.amazonaws.services.s3.AmazonS3Client;
import com.netflix.eureka2.aws.MockS3Service;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
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

    private final AwsConfiguration config = mock(AwsConfiguration.class);

    private final MockS3Service mockS3Service = new MockS3Service();
    private S3StatusOverridesRegistry registry;

    @Before
    public void setUp() throws Exception {
        when(config.getRefreshIntervalSec()).thenReturn(REFRESH_INTERVAL_SEC);
        when(config.getBucketName()).thenReturn("myBucketName");
        when(config.getPrefix()).thenReturn("eureka2.overrides.test");

        AmazonS3Client amazonS3Client = mockS3Service.getAmazonS3Client();
        registry = new S3StatusOverridesRegistry(amazonS3Client, config, testScheduler);

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
        assertThat(mockS3Service.getContentsView().size(), is(1));
        assertThat(mockS3Service.getContentsView().keySet(), containsInAnyOrder(
                registry.toS3Name(config.getPrefix(), instanceInfo1.getId())
        ));

        registry.setOutOfService(instanceInfo2).subscribe();
        assertThat(mockS3Service.getContentsView().size(), is(2));
        assertThat(mockS3Service.getContentsView().keySet(), containsInAnyOrder(
                registry.toS3Name(config.getPrefix(), instanceInfo1.getId()),
                registry.toS3Name(config.getPrefix(), instanceInfo2.getId())
        ));

        registry.unsetOutOfService(instanceInfo1).subscribe();
        assertThat(mockS3Service.getContentsView().size(), is(1));
        assertThat(mockS3Service.getContentsView().keySet(), containsInAnyOrder(
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
}
