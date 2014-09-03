/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka.transport.codec.json;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.netflix.eureka.transport.Acknowledgement;
import com.netflix.eureka.utils.Json;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.DeserializerFactory;
import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.SerializationConfig.Feature;
import org.codehaus.jackson.map.SerializerFactory;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.deser.BeanDeserializerBuilder;
import org.codehaus.jackson.map.deser.BeanDeserializerFactory;
import org.codehaus.jackson.map.deser.BeanDeserializerModifier;
import org.codehaus.jackson.map.deser.SettableBeanProperty;
import org.codehaus.jackson.map.deser.StdDeserializerProvider;
import org.codehaus.jackson.map.introspect.BasicBeanDescription;
import org.codehaus.jackson.map.module.SimpleSerializers;
import org.codehaus.jackson.map.ser.BeanSerializerFactory;
import org.codehaus.jackson.map.ser.BeanSerializerModifier;
import org.codehaus.jackson.map.ser.std.BeanSerializerBase;
import org.codehaus.jackson.node.ArrayNode;

/**
 * Codec useful for development purposes. It could be also used by WEB browser
 * based clients communicating over WebSocket.
 *
 * @author Tomasz Bak
 */
public class JsonCodec extends ByteToMessageCodec<Object> {

    private final ObjectMapper mapper;
    private final Set<Class<?>> protocolTypes;

    public JsonCodec(Set<Class<?>> protocolTypes) {
        this.protocolTypes = protocolTypes;
        mapper = new ObjectMapper();

        SimpleSerializers serializers = new SimpleSerializers();
        serializers.addSerializer(Enum.class, new EnumSerializer());
        SerializerFactory serializerFactory = BeanSerializerFactory
                .instance
                .withAdditionalSerializers(serializers)
                .withSerializerModifier(new TypeInjectingModifier());

        DeserializerFactory deserializerFactory = BeanDeserializerFactory
                .instance
                .withDeserializerModifier(new TypeResolvingModifier(mapper));

        mapper.setSerializerFactory(serializerFactory);
        mapper.setDeserializerProvider(new StdDeserializerProvider(deserializerFactory));
        mapper.setVisibility(JsonMethod.FIELD, Visibility.ANY);
        mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(Feature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(Feature.AUTO_DETECT_GETTERS, false);
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof Acknowledgement || protocolTypes.contains(msg.getClass());
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(msg);
        out.writeBytes(bytes);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        byte[] array = new byte[in.readableBytes()];
        in.readBytes(array);

        String json = new String(array, Charset.defaultCharset());
        JsonNode jsonNode = Json.getMapper().readTree(json);

        String messageType = jsonNode.get("_type").asText();
        Class<?> contentClass = Class.forName(messageType);
        Object content = mapper.readValue(jsonNode, contentClass);
        out.add(content);
    }


    static class EnumSerializer extends JsonSerializer<Enum> {
        @Override
        public void serialize(Enum value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeStartObject();
            jgen.writeStringField("_type", value.getClass().getName());
            jgen.writeStringField("value", value.toString());
            jgen.writeEndObject();
        }
    }

    static class TypeInjectingModifier extends BeanSerializerModifier {
        @Override
        public JsonSerializer<?> modifySerializer(SerializationConfig config, BasicBeanDescription beanDesc, JsonSerializer<?> serializer) {
            if (serializer == null) {
                return new EmptyBeanSerializer();
            }
            return new TypeInjectingSerializer((BeanSerializerBase) serializer);
        }

        static class TypeInjectingSerializer extends BeanSerializerBase {

            TypeInjectingSerializer(BeanSerializerBase source) {
                super(source);
            }

            @Override
            public void serialize(Object bean, JsonGenerator jgen, SerializerProvider provider) throws IOException {
                jgen.writeStartObject();
                serializeFields(bean, jgen, provider);
                jgen.writeStringField("_type", bean.getClass().getName());
                jgen.writeEndObject();
            }
        }

        static class EmptyBeanSerializer extends JsonSerializer<Object> {
            @Override
            public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
                jgen.writeStartObject();
                jgen.writeStringField("_type", value.getClass().getName());
                jgen.writeEndObject();
            }
        }
    }

    static class PolymorphicDeserializer extends JsonDeserializer<Object> {

        private final Class<?> rawClass;
        private final ObjectMapper mapper;

        PolymorphicDeserializer(Class<?> rawClass, ObjectMapper mapper) {
            this.rawClass = rawClass;
            this.mapper = mapper;
        }

        @Override
        public Object deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            JsonNode tree = jp.readValueAsTree();
            if (tree instanceof ArrayNode) {
                return handleArray(jp, (ArrayNode) tree);
            }
            return handleObject(jp, tree);
        }

        private Object handleObject(JsonParser jp, JsonNode tree) throws IOException {
            Class<?> objectClass = getObjectType(jp, tree);
            if (objectClass.isEnum()) {
                return deserializeEnum(jp, tree);
            }
            return mapper.readValue(tree, objectClass);
        }

        private Object handleArray(JsonParser jp, ArrayNode arrayNode) throws IOException {
            Object[] arrayInstance = (Object[]) Array.newInstance(rawClass.getComponentType(), arrayNode.size());
            for (int i = 0; i < arrayInstance.length; i++) {
                Object value = handleObject(jp, arrayNode.get(i));
                arrayInstance[i] = value;
            }
            return arrayInstance;
        }
    }

    static class TypeResolvingModifier extends BeanDeserializerModifier {

        private final ObjectMapper mapper;

        TypeResolvingModifier(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public BeanDeserializerBuilder updateBuilder(DeserializationConfig config, BasicBeanDescription beanDesc, BeanDeserializerBuilder builder) {
            Iterator<SettableBeanProperty> beanPropertyIterator = builder.getProperties();
            while (beanPropertyIterator.hasNext()) {
                SettableBeanProperty settableBeanProperty = beanPropertyIterator.next();
                Class<?> rawClass = settableBeanProperty.getType().getRawClass();
                if (!isBasicType(rawClass)) {
                    SettableBeanProperty newSettableBeanProperty = settableBeanProperty.withValueDeserializer(new PolymorphicDeserializer(rawClass, mapper));
                    builder.addOrReplaceProperty(newSettableBeanProperty, true);
                }
            }
            return builder;
        }

        private boolean isBasicType(Class<?> rawClass) {
            Class<?> type = rawClass.isArray() ? rawClass.getComponentType() : rawClass;
            if (type.isPrimitive() || type.equals(String.class)) {
                return true;
            }
            if (Number.class.isAssignableFrom(rawClass)) {
                return true;
            }
            if (Collection.class.isAssignableFrom(rawClass)) {
                return true;
            }

            return false;
        }
    }

    static class EnumDeserializer extends JsonDeserializer<Enum> {

        @Override
        public Enum deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            JsonNode tree = jp.readValueAsTree();
            return deserializeEnum(jp, tree);
        }
    }

    private static Class<?> getObjectType(JsonParser jp, JsonNode tree) throws JsonParseException {
        String type = tree.get("_type").asText();
        Class<?> objectClass;
        try {
            objectClass = Class.forName(type);
        } catch (ClassNotFoundException e) {
            throw new JsonParseException("Cannot instantiate type " + type, jp.getCurrentLocation());
        }
        return objectClass;
    }

    private static Enum deserializeEnum(JsonParser jp, JsonNode tree) throws IOException {
        Class<Enum> enumType = (Class<Enum>) getObjectType(jp, tree);
        String enumValue = tree.get("value").asText();
        for (Enum value : enumType.getEnumConstants()) {
            if (value.name().equals(enumValue)) {
                return value;
            }
        }
        throw new JsonMappingException(String.format("Unrecognized enum value for type %s for type %s", enumValue, enumType));
    }
}
