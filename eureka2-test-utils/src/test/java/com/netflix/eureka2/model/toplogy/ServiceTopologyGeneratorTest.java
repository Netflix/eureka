package com.netflix.eureka2.model.toplogy;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.netflix.eureka2.model.instance.InstanceInfo;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class ServiceTopologyGeneratorTest {

    private static final ApplicationProfile BIG_APP_PROFILE = new ApplicationProfile("bigApp", 10, 1);
    private static final ApplicationProfile SMALL_APP_PROFILE = new ApplicationProfile("smallApp", 2, 10);
    private static final List<ApplicationProfile> APPLICATION_PROFILES = asList(BIG_APP_PROFILE, SMALL_APP_PROFILE);

    private static final int SERVICES_TOTAL = BIG_APP_PROFILE.getServiceCount() + SMALL_APP_PROFILE.getServiceCount();

    private final DataCenterInfoGenerator dataCenterInfoGenerator = new DataCenterInfoGenerator();

    @Test
    public void testGeneratesServicesAccordingToProfile() throws Exception {
        int multiplier = 2;
        ServiceTopologyGenerator generator = new ServiceTopologyGenerator(
                "testTopology",
                "testServer",
                APPLICATION_PROFILES,
                Collections.<DependencyProfile>emptyList(),
                dataCenterInfoGenerator,
                multiplier
        );

        Iterator<InstanceInfo> serviceIterator = generator.serviceIterator();

        int total = SERVICES_TOTAL * multiplier;
        int bigCount = 0;
        int smallCount = 0;
        for (int i = 0; i < total; i++) {
            assertThat(serviceIterator.hasNext(), is(true));
            InstanceInfo next = serviceIterator.next();
            if (next.getApp().contains(BIG_APP_PROFILE.getApplicationName())) {
                bigCount++;
            } else if (next.getApp().contains(SMALL_APP_PROFILE.getApplicationName())) {
                smallCount++;
            } else {
                fail("Unexpected " + next);
            }
        }

        assertThat(bigCount, is(equalTo(BIG_APP_PROFILE.getServiceCount() * multiplier)));
        assertThat(smallCount, is(equalTo(SMALL_APP_PROFILE.getServiceCount() * multiplier)));
    }
}