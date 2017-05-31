package com.netflix.discovery.converters.jackson;

import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.ClassNameIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.MyDataCenterInfo;

import java.io.IOException;

/**
 * @author Tomasz Bak
 */
public class DataCenterTypeInfoResolver extends ClassNameIdResolver {

    /**
     * This phantom class name is kept for backwards compatibility. Internally it is mapped to
     * {@link MyDataCenterInfo} during the deserialization process.
     */
    public static final String MY_DATA_CENTER_INFO_TYPE_MARKER = "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo";

    public DataCenterTypeInfoResolver() {
        super(TypeFactory.defaultInstance().constructType(DataCenterInfo.class), TypeFactory.defaultInstance());
    }

    @Override
    public JavaType typeFromId(DatabindContext context, String id) throws IOException {
        if (MY_DATA_CENTER_INFO_TYPE_MARKER.equals(id)) {
            return context.getTypeFactory().constructType(MyDataCenterInfo.class);
        }
        return super.typeFromId(context, id);
    }

    @Override
    public String idFromValue(Object value) {
        if (value.getClass().equals(AmazonInfo.class)) {
            return AmazonInfo.class.getName();
        }
        return MY_DATA_CENTER_INFO_TYPE_MARKER;
    }
}
