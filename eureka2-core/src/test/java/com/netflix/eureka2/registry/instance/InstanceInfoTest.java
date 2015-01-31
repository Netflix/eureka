package com.netflix.eureka2.registry.instance;

import java.util.Set;

import com.netflix.eureka2.testkit.data.builder.SampleAwsDataCenterInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleServicePort;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * @author David Liu
 */
public class InstanceInfoTest {
    @Test(timeout = 60000)
    public void testApplyDelta() throws Exception {
        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();

        Delta delta = new Delta.Builder()
                .withId(instanceInfo.getId())
                .withDelta(InstanceInfoField.STATUS, InstanceInfo.Status.OUT_OF_SERVICE)
                .build();

        InstanceInfo afterDelta = instanceInfo.applyDelta(delta);
        assertThat(afterDelta.getStatus(), equalTo(InstanceInfo.Status.OUT_OF_SERVICE));
    }

    @Test(timeout = 60000)
    public void testProduceNullDeltasIfMismatchedIds() throws Exception {
        InstanceInfo oldInstanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        InstanceInfo newInstanceInfo = SampleInstanceInfo.ZuulServer.build();  // different id

        Set<Delta<?>> deltas = oldInstanceInfo.diffNewer(newInstanceInfo);
        assertThat(deltas, nullValue());
    }

    @Test(timeout = 60000)
    public void testProduceSetOfDeltas() throws Exception {
        InstanceInfo oldInstanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        Thread.sleep(2);  // let time elapse a bit for version timestamp to advance

        // fake a new InstanceInfo that is different in all fields (except id)
        InstanceInfo newInstanceInfo = SampleInstanceInfo.ZuulServer.builder()
                .withId(oldInstanceInfo.getId())
                .withPorts(SampleServicePort.httpPorts())
                .withStatus(InstanceInfo.Status.DOWN)
                .withMetaData(null)
                .withDataCenterInfo(SampleAwsDataCenterInfo.UsEast1c.build())
                .build();

        Set<Delta<?>> deltas = oldInstanceInfo.diffNewer(newInstanceInfo);
        assertThat(deltas.size(), equalTo(12));

        for (Delta<?> delta : deltas) {
            oldInstanceInfo = oldInstanceInfo.applyDelta(delta);
        }

        assertThat(oldInstanceInfo, equalTo(newInstanceInfo));
    }
}
