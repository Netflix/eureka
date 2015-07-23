package com.netflix.discovery.converters;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumMap;
import java.util.Map;

import com.netflix.discovery.converters.jackson.EurekaJacksonCodecNG;

import javax.ws.rs.core.MediaType;

/**
 * @author Tomasz Bak
 */
public abstract class CodecWrapper {

    public enum CodecType {
        XStreamJson,
        XStreamXml,
        LegacyJacksonJson,
        JacksonJson,
        JacksonXml;

        public static CodecType from(String name) {
            try {
                return CodecType.valueOf(name);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static final Map<CodecType, CodecWrapper> jsonCodecsByType = new EnumMap<CodecType, CodecWrapper>(CodecType.class);
    private static final Map<CodecType, CodecWrapper> xmlCodecsByType = new EnumMap<CodecType, CodecWrapper>(CodecType.class);

    static {
        xmlCodecsByType.put(CodecType.XStreamXml, new XStreamXmlWrapper());
        xmlCodecsByType.put(CodecType.JacksonXml, new JacksonXmlWrapper());

        jsonCodecsByType.put(CodecType.XStreamJson, new XStreamJsonWrapper());
        jsonCodecsByType.put(CodecType.LegacyJacksonJson, new LegacyJacksonJsonWrapper());
        jsonCodecsByType.put(CodecType.JacksonJson, new JacksonJsonWrapper());
    }

    public abstract CodecType getCodecType();

    public abstract <T> String encode(T object) throws IOException;

    public abstract <T> void encode(T object, OutputStream outputStream) throws IOException;

    public abstract <T> T decode(String textValue, Class<T> type) throws IOException;

    public abstract <T> T decode(InputStream inputStream, Class<T> type) throws IOException;

    public static CodecWrapper[] availableJsonCodecs() {
        return jsonCodecsByType.values().toArray(new CodecWrapper[jsonCodecsByType.size()]);
    }

    public static CodecWrapper[] availableXmlCodecs() {
        return xmlCodecsByType.values().toArray(new CodecWrapper[xmlCodecsByType.size()]);
    }

    public static CodecWrapper get(CodecType type) {
        if (type == null) {
            return null;
        }

        CodecWrapper wrapper = jsonCodecsByType.get(type);
        if (wrapper == null) {
            wrapper = xmlCodecsByType.get(type);
        }
        return wrapper;
    }


    public static class XStreamXmlWrapper extends CodecWrapper {

        @Override
        public CodecType getCodecType() {
            return CodecType.XStreamXml;
        }

        @Override
        public <T> String encode(T object) throws IOException {
            return XmlXStream.getInstance().toXML(object);
        }

        @Override
        public <T> void encode(T object, OutputStream outputStream) throws IOException {
            XmlXStream.getInstance().toXML(object, outputStream);
        }

        @Override
        public <T> T decode(String textValue, Class<T> type) throws IOException {
            return (T) XmlXStream.getInstance().fromXML(textValue, type);
        }

        @Override
        public <T> T decode(InputStream inputStream, Class<T> type) throws IOException {
            return (T) XmlXStream.getInstance().fromXML(inputStream, type);
        }
    }

    public static class XStreamJsonWrapper extends CodecWrapper {

        @Override
        public CodecType getCodecType() {
            return CodecType.XStreamJson;
        }

        @Override
        public <T> String encode(T object) throws IOException {
            return JsonXStream.getInstance().toXML(object);
        }

        @Override
        public <T> void encode(T object, OutputStream outputStream) throws IOException {
            JsonXStream.getInstance().toXML(object, outputStream);
        }

        @Override
        public <T> T decode(String textValue, Class<T> type) throws IOException {
            return (T) JsonXStream.getInstance().fromXML(textValue, type);
        }

        @Override
        public <T> T decode(InputStream inputStream, Class<T> type) throws IOException {
            return (T) JsonXStream.getInstance().fromXML(inputStream, type);
        }
    }

    public static class LegacyJacksonJsonWrapper extends CodecWrapper {

        private final EurekaJacksonCodec codec = new EurekaJacksonCodec();

        @Override
        public CodecType getCodecType() {
            return CodecType.LegacyJacksonJson;
        }

        @Override
        public <T> String encode(T object) throws IOException {
            return codec.writeToString(object);
        }

        @Override
        public <T> void encode(T object, OutputStream outputStream) throws IOException {
            codec.writeTo(object, outputStream);
        }

        @Override
        public <T> T decode(String textValue, Class<T> type) throws IOException {
            return codec.readValue(type, textValue);
        }

        @Override
        public <T> T decode(InputStream inputStream, Class<T> type) throws IOException {
            return codec.readValue(type, inputStream);
        }
    }

    public static class JacksonJsonWrapper extends CodecWrapper {

        private final EurekaJacksonCodecNG codec = new EurekaJacksonCodecNG();

        @Override
        public CodecType getCodecType() {
            return CodecType.JacksonJson;
        }

        @Override
        public <T> String encode(T object) throws IOException {
            return codec.getJsonMapper().writeValueAsString(object);
        }

        @Override
        public <T> void encode(T object, OutputStream outputStream) throws IOException {
            codec.writeTo(object, outputStream, MediaType.APPLICATION_JSON_TYPE);
        }

        @Override
        public <T> T decode(String textValue, Class<T> type) throws IOException {
            return codec.getJsonMapper().readValue(textValue, type);
        }

        @Override
        public <T> T decode(InputStream inputStream, Class<T> type) throws IOException {
            return codec.getJsonMapper().readValue(inputStream, type);
        }
    }

    public static class JacksonXmlWrapper extends CodecWrapper {

        private final EurekaJacksonCodecNG codec = new EurekaJacksonCodecNG();

        @Override
        public CodecType getCodecType() {
            return CodecType.JacksonXml;
        }

        @Override
        public <T> String encode(T object) throws IOException {
            return codec.getXmlMapper().writeValueAsString(object);
        }

        @Override
        public <T> void encode(T object, OutputStream outputStream) throws IOException {
            codec.writeTo(object, outputStream, MediaType.APPLICATION_XML_TYPE);
        }

        @Override
        public <T> T decode(String textValue, Class<T> type) throws IOException {
            return codec.getXmlMapper().readValue(textValue, type);
        }

        @Override
        public <T> T decode(InputStream inputStream, Class<T> type) throws IOException {
            return codec.getXmlMapper().readValue(inputStream, type);
        }
    }
}
