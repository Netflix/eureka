package com.netflix.eureka2.testkit.data.builder;

import java.util.Iterator;

import com.netflix.eureka2.model.datacenter.AwsDataCenterInfo;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class SampleAwsDataCenterInfoTest {

    @Test(timeout = 60000)
    public void testGeneratesSubsequentDataCenterInfos() throws Exception {
        Iterator<AwsDataCenterInfo> iterator =
                SampleAwsDataCenterInfo.collectionOf("test", SampleAwsDataCenterInfo.UsEast1a.build());

        AwsDataCenterInfo first = iterator.next();
        assertThat(first.getPublicAddress().getHostName(), containsString("test"));
        assertThat(first.getInstanceId(), is(equalTo("id-00000001")));

        AwsDataCenterInfo second = iterator.next();
        assertThat(second.getInstanceId(), is(equalTo("id-00000002")));
    }
}