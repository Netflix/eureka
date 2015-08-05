package com.netflix.discovery.converters.jackson;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.KeyFormatter;

/**
 * @author Tomasz Bak
 */
public class EurekaJacksonCodecNG {

    private final ObjectMapper jsonMapper = new ObjectMapper();

    private final XmlMapper xmlMapper;

    private static final Set<String> MINI_AMAZON_INFO_INCLUDE_KEYS = new HashSet<>(
            Arrays.asList("instance-id", "public-ipv4", "public-hostname", "local-ipv4", "availability-zone")
    );

    public EurekaJacksonCodecNG() {
        this(KeyFormatter.defaultKeyFormatter(), false);
    }

    public EurekaJacksonCodecNG(final KeyFormatter keyFormatter, boolean compact) {
        // JSON
        SimpleModule jsonModule = new SimpleModule();
        jsonModule.setSerializerModifier(EurekaJacksonModifiers.createJsonSerializerModifier(keyFormatter));
        jsonModule.setDeserializerModifier(EurekaJacksonModifiers.createJsonDeserializerModifier(keyFormatter, compact));
        jsonMapper.registerModule(jsonModule);
        jsonMapper.setSerializationInclusion(Include.NON_NULL);
        jsonMapper.configure(SerializationFeature.WRAP_ROOT_VALUE, true);
        jsonMapper.configure(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED, true);
        jsonMapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true);
        jsonMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

        // XML
        xmlMapper = new XmlMapper() {
            public ObjectMapper registerModule(Module module) {
                setSerializerFactory(
                        getSerializerFactory().withSerializerModifier(EurekaJacksonModifiers.createXmlSerializerModifier(keyFormatter))
                );
                return super.registerModule(module);
            }
        };
        xmlMapper.setSerializationInclusion(Include.NON_NULL);
        xmlMapper.addMixInAnnotations(DataCenterInfo.class, DataCenterInfoXmlMixIn.class);
        SimpleModule xmlModule = new SimpleModule();
        xmlModule.setDeserializerModifier(EurekaJacksonModifiers.createXmlDeserializerModifier(keyFormatter, compact));
        xmlMapper.registerModule(xmlModule);

        if (compact) {
            addMiniConfig(jsonMapper);
            addMiniConfig(xmlMapper);
        }
    }

    public ObjectMapper getJsonMapper() {
        return jsonMapper;
    }

    public ObjectMapper getXmlMapper() {
        return xmlMapper;
    }

    public <T> T readValue(Class<T> type, InputStream entityStream, MediaType mediaType) throws IOException {
        return getMapper(mediaType).readValue(entityStream, type);
    }

    public <T> void writeTo(T object, OutputStream entityStream, MediaType mediaType) throws IOException {
        getMapper(mediaType).writeValue(entityStream, object);
    }

    private void addMiniConfig(ObjectMapper mapper) {
        mapper.addMixInAnnotations(InstanceInfo.class, MiniInstanceInfoMixIn.class);
        bindAmazonInfoFilter(mapper);
    }

    private void bindAmazonInfoFilter(ObjectMapper mapper) {
        SimpleFilterProvider filters = new SimpleFilterProvider();
        final String filterName = "exclude-meta-info-entries";
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

    private ObjectMapper getMapper(MediaType mediaType) {
        if (mediaType.equals(MediaType.APPLICATION_JSON_TYPE)) {
            return jsonMapper;
        }
        if (mediaType.equals(MediaType.APPLICATION_XML_TYPE)) {
            return xmlMapper;
        }
        throw new IllegalArgumentException("Expected application/xml or application/json media types only, and got " + mediaType);
    }
}
