package com.netflix.eureka2.metric;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Spectator;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class SpectatorEurekaMetricsTest {
    @Test
    public void testMetricsClassIdIsDerivedFromInterfaceName() throws Exception {
        SpectatorEurekaRegistryMetrics eurekaMetrics = new SpectatorEurekaRegistryMetrics(Spectator.registry());
        Id actualId = eurekaMetrics.newId("testName");

        Id expectedId = Spectator.registry().createId("testName")
                .withTag("id", "eurekaServerRegistry")
                .withTag("class", EurekaRegistryMetrics.class.getSimpleName());

        assertThat(actualId, is(equalTo(expectedId)));
    }
}