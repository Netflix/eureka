package com.netflix.eureka2.model.toplogy;

import java.util.Iterator;

import com.netflix.eureka2.model.datacenter.AwsDataCenterInfo;
import com.netflix.eureka2.model.datacenter.DataCenterInfo;
import com.netflix.eureka2.testkit.data.builder.SampleAwsDataCenterInfo;

/**
 * Infinite generator of {@link DataCenterInfo} instances.
 *
 * @author Tomasz Bak
 */
public class DataCenterInfoGenerator implements Iterator<DataCenterInfo> {

    private final Iterator<AwsDataCenterInfo> iterator;

    public DataCenterInfoGenerator() {
        iterator = SampleAwsDataCenterInfo.collectionOf("test", SampleAwsDataCenterInfo.UsEast1a.build());
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public DataCenterInfo next() {
        return iterator.next();
    }

    @Override
    public void remove() {
    }
}
