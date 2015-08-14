package com.netflix.eureka2.codec.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.netflix.eureka2.codec.EurekaCodec;
import com.netflix.eureka2.registry.MultiSourcedDataHolder;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig.Feature;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

/**
 * {@link MultiSourcedDataHolder} objects are exposed for diagnostic purposes, but are not exchanged
 * between eureka entities (server to server or server to client). Thus only serializer part is provided
 * by this codec.
 *
 * @author Tomasz Bak
 */
public class MultiSourcedDataHolderJsonCodec implements EurekaCodec<MultiSourcedDataHolder<InstanceInfo>> {

    private final boolean compact;
    private final ObjectMapper mapper;

    public MultiSourcedDataHolderJsonCodec(boolean compact) {
        this.compact = compact;

        this.mapper = new ObjectMapper();
        this.mapper.setVisibility(JsonMethod.FIELD, Visibility.ANY);
        this.mapper.configure(Feature.FAIL_ON_EMPTY_BEANS, false);
        this.mapper.setSerializationInclusion(Inclusion.NON_NULL);
    }

    @Override
    public boolean accept(Class<?> valueType) {
        return MultiSourcedDataHolder.class.equals(valueType);
    }

    @Override
    public void encode(MultiSourcedDataHolder<InstanceInfo> value, OutputStream output) throws IOException {
        Object wrapper = compact ? buildCompactWrapper(value) : buildFullWrapper(value);
        mapper.writeValue(output, wrapper);
    }

    private Object buildFullWrapper(MultiSourcedDataHolder<InstanceInfo> value) {
        Collection<Source> allSources = value.getAllSources();
        List<SourceInstanceEnvelope> wrapper = new ArrayList<>();
        for (Source source : allSources) {
            wrapper.add(new SourceInstanceEnvelope(source, value.get(source)));
        }
        return wrapper;
    }

    private Object buildCompactWrapper(MultiSourcedDataHolder<InstanceInfo> value) {
        return new InstanceSummaryWithSourcesEnvelope(value.getAllSources(), value.get());
    }

    @Override
    public MultiSourcedDataHolder<InstanceInfo> decode(InputStream source) throws IOException {
        throw new IllegalStateException("InstanceInfo holder decoding not supported");
    }

    private static class SourceInstanceEnvelope {
        private final Source source;
        private final InstanceInfo instanceInfo;

        SourceInstanceEnvelope(Source source, InstanceInfo instanceInfo) {
            this.source = source;
            this.instanceInfo = instanceInfo;
        }
    }

    private static class InstanceSummaryWithSourcesEnvelope {
        private final Collection<Source> sources;
        private final String id;
        private final String app;

        InstanceSummaryWithSourcesEnvelope(Collection<Source> sources, InstanceInfo instanceInfo) {
            this.sources = sources;
            this.id = instanceInfo.getId();
            this.app = instanceInfo.getApp();
        }
    }
}
