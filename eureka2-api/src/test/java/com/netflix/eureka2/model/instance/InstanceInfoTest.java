/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.model.instance;

import java.util.Set;

import com.netflix.eureka2.model.InstanceModel;
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

    @Test
    public void testApplyDelta() throws Exception {
        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();

        Delta delta = InstanceModel.getDefaultModel().newDelta()
                .withId(instanceInfo.getId())
                .withDelta(InstanceInfoField.STATUS, InstanceInfo.Status.OUT_OF_SERVICE)
                .build();

        InstanceInfo afterDelta = instanceInfo.applyDelta(delta);
        assertThat(afterDelta.getStatus(), equalTo(InstanceInfo.Status.OUT_OF_SERVICE));
    }

    @Test
    public void testProduceNullDeltasIfMismatchedIds() throws Exception {
        InstanceInfo oldInstanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        InstanceInfo newInstanceInfo = SampleInstanceInfo.ZuulServer.build();  // different id

        Set<Delta<?>> deltas = oldInstanceInfo.diffNewer(newInstanceInfo);
        assertThat(deltas, nullValue());
    }

    @Test
    public void testProduceSetOfDeltas() throws Exception {
        InstanceInfo oldInstanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        Thread.sleep(2);  // let time elapse a bit for version timestamp to advance

        // fake a new IInstanceInfo that is different in all fields (except id)
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
