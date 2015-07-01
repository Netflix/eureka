package com.netflix.discovery.converters;

import com.fasterxml.jackson.databind.jsontype.impl.ClassNameIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.MyDataCenterInfo;

/**
 * @author Tomasz Bak
 */
public class DataCenterTypeInfoResolver extends ClassNameIdResolver {
    public DataCenterTypeInfoResolver() {
        super(TypeFactory.defaultInstance().constructType(DataCenterInfo.class), TypeFactory.defaultInstance());
    }

    @Override
    public String idFromValue(Object value) {
        if (value.getClass().equals(AmazonInfo.class)) {
            return AmazonInfo.class.getName();
        }
        return MyDataCenterInfo.class.getName();
    }
}
