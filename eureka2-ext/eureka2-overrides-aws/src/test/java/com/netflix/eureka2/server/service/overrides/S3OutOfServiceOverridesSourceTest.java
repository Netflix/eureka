package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.testkit.data.builder.SampleOverrides;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Ignored as these tests needs an actual s3 backing to execute. Uncomment ignore and execute with the correct
 * S3 configs if you have the necessary set up.
 *
 * @author David Liu
 */
@Ignore
public class S3OutOfServiceOverridesSourceTest {

    private LoadingOverridesRegistry.ExternalOverridesSource overridesSource;

    @Before
    public void setUp() {
        S3OverridesConfig config = new S3OverridesConfig() {
            @Override
            public String getBucketName() {
                return "my-bucket-name";
            }

            @Override
            public String getPrefix() {
                return "my-prefix";
            }
        };
        overridesSource = new S3OutOfServiceOverridesSource(config);
    }

    /**
     * A lifecycle test that puts and removes various elements and does asMap listings in between.
     */
    @Test
    public void testLifecycle() {
        Overrides overrides1 = SampleOverrides.generateOverrides("id-1aaaa", 1);
        Overrides overrides2 = SampleOverrides.generateOverrides("id-2aaaa", 1);
        Overrides overrides3 = SampleOverrides.generateOverrides("id-3aaaa", 1);

        try {
            overridesSource.set(overrides1);
            overridesSource.set(overrides2);
            overridesSource.set(overrides3);

            Map<String, Overrides> overridesMap = overridesSource.asMap();

            assertThat(overridesMap.size(), is(3));
            assertThat(overridesMap.values(), containsInAnyOrder(overrides1, overrides2, overrides3));
        } catch (Exception e) {
            Assert.fail("failed due to exception: " + e);
        } finally {
            try {
                overridesSource.remove(overrides1.getId());
                overridesSource.remove(overrides2.getId());
                overridesSource.remove(overrides3.getId());

                Map<String, Overrides> overridesMap = overridesSource.asMap();
                assertThat(overridesMap.size(), is(0));
            } catch (Exception e) {
                Assert.fail("failed due to exception: " + e);
            }
        }
    }
}
