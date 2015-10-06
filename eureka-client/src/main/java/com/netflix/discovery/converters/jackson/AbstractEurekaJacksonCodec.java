package com.netflix.discovery.converters.jackson;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.jackson.mixin.MiniInstanceInfoMixIn;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractEurekaJacksonCodec {

    protected static final Set<String> MINI_AMAZON_INFO_INCLUDE_KEYS = new HashSet<>(
            Arrays.asList("instance-id", "public-ipv4", "public-hostname", "local-ipv4", "availability-zone")
    );

    public abstract <T> ObjectMapper getObjectMapper(Class<T> type);

    public <T> void writeTo(T object, OutputStream entityStream) throws IOException {
        getObjectMapper(object.getClass()).writeValue(entityStream, object);
    }

    protected void addMiniConfig(ObjectMapper mapper) {
        mapper.addMixIn(InstanceInfo.class, MiniInstanceInfoMixIn.class);
        bindAmazonInfoFilter(mapper);
    }

    private void bindAmazonInfoFilter(ObjectMapper mapper) {
        SimpleFilterProvider filters = new SimpleFilterProvider();
        final String filterName = "exclude-amazon-info-entries";
        mapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
            @Override
            public Object findFilterId(Annotated a) {
                if (Map.class.isAssignableFrom(a.getRawType())) {
                    return filterName;
                }
                return super.findFilterId(a);
            }
        });
        filters.addFilter(filterName, new SimpleBeanPropertyFilter() {
            @Override
            protected boolean include(BeanPropertyWriter writer) {
                return true;
            }

            @Override
            protected boolean include(PropertyWriter writer) {
                return MINI_AMAZON_INFO_INCLUDE_KEYS.contains(writer.getName());
            }
        });
        mapper.setFilters(filters);
    }

    static boolean hasJsonRootName(Class<?> type) {
        return type.getAnnotation(JsonRootName.class) != null;
    }
}
