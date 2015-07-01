package com.netflix.discovery.converters;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

/**
 * @author Tomasz Bak
 */
public class EurekaJacksonCodecNG {

    private final ObjectMapper jsonMapper = new ObjectMapper();

    private final XmlMapper xmlMapper = new XmlMapper() {
        public ObjectMapper registerModule(Module module) {
            setSerializerFactory(
                    getSerializerFactory().withSerializerModifier(InstanceInfoBeanSerializer.createXmlSerializer())
            );
            return super.registerModule(module);
        }
    };

    private static final Set<String> MINI_META_INFO_KEYS = new HashSet<>(
            Arrays.asList("accountId", "instance-id", "ami-id", "instance-type")
    );

    public EurekaJacksonCodecNG() {
        this(false);
    }

    public EurekaJacksonCodecNG(boolean compact) {
        // JSON
        SimpleModule jsonModule = new SimpleModule();
        jsonModule.setSerializerModifier(InstanceInfoBeanSerializer.createJsonSerializer());
        jsonModule.setDeserializerModifier(InstanceInfoBeanDeserializer.createJsonDeserializerModifier());
        jsonMapper.registerModule(jsonModule);
        jsonMapper.configure(SerializationFeature.WRAP_ROOT_VALUE, true);
        jsonMapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true);

        // XML
        xmlMapper.addMixInAnnotations(DataCenterInfo.class, DataCenterInfoXmlMixIn.class);
        SimpleModule xmlModule = new SimpleModule();
        xmlModule.setDeserializerModifier(InstanceInfoBeanDeserializer.createXmlDeserializerModifier());
        xmlMapper.registerModule(xmlModule);

        if (compact) {
            addMiniConfig(jsonMapper);
            addMiniConfig(xmlMapper);
        }
    }

    private void addMiniConfig(ObjectMapper mapper) {
        mapper.addMixInAnnotations(InstanceInfo.class, MiniInstanceInfoMixIn.class);
        bindMetaInfoFilter(mapper);
    }

    private void bindMetaInfoFilter(ObjectMapper mapper) {
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
                return MINI_META_INFO_KEYS.contains(writer.getName());
            }
        });
        mapper.setFilters(filters);
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
