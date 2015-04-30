package com.netflix.eureka2.codec.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.netflix.eureka2.codec.EurekaCodec;
import com.netflix.eureka2.utils.Json;
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
 * @author Tomasz Bak
 */
public class EurekaJsonCodec<T> implements EurekaCodec<T> {
    private final ObjectMapper mapper;
    private final Set<Class<?>> acceptedTypes;

    public EurekaJsonCodec(Set<Class<?>> acceptedTypes) {
        this.acceptedTypes = acceptedTypes;
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
    public boolean accept(Class<?> valueType) {
        return acceptedTypes.contains(valueType);
    }

    @Override
    public void encode(T value, OutputStream output) throws IOException {
        mapper.writeValue(output, value);
    }

    public <C> void encodeContainer(C container, OutputStream output) throws IOException {
        mapper.writeValue(output, container);
    }

    @Override
    public T decode(InputStream source) throws IOException {
        return (T) decodeJson(source);
    }

    public <C> C decodeContainer(Class<C> containerType, InputStream source) throws IOException {
        return (C) decodeJson(source);
    }

    private Object decodeJson(InputStream source) throws IOException {
        JsonNode jsonNode = Json.getMapper().readTree(source);

        String messageType = jsonNode.get("_type").asText();
        Class<?> contentClass;
        try {
            contentClass = Class.forName(messageType);
        } catch (ClassNotFoundException e) {
            throw new IOException("Incompatible encoded value format", e);
        }
        return mapper.readValue(jsonNode, contentClass);
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
            if (Map.class.isAssignableFrom(rawClass)) {
                return true;
            }

            return false;
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
