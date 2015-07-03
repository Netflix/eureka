package com.netflix.discovery.converters;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import com.netflix.discovery.converters.jackson.EurekaJacksonCodecNG;

/**
 * @author Tomasz Bak
 */
public abstract class CodecWrapper {

    enum CodecType {
        XStreamJson,
        XStreamXml,
        LegacyJacksonJson,
        JacksonJson,
        JacksonXml
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

    public abstract <T> T decode(String textValue, Class<T> type) throws IOException;

    public static CodecWrapper[] availableJsonCodecs() {
        return jsonCodecsByType.values().toArray(new CodecWrapper[jsonCodecsByType.size()]);
    }

    public static CodecWrapper[] availableXmlCodecs() {
        return xmlCodecsByType.values().toArray(new CodecWrapper[xmlCodecsByType.size()]);
    }

    static class XStreamXmlWrapper extends CodecWrapper {

        @Override
        public CodecType getCodecType() {
            return CodecType.XStreamXml;
        }

        @Override
        public <T> String encode(T object) throws IOException {
            return XmlXStream.getInstance().toXML(object);
        }

        @Override
        public <T> T decode(String textValue, Class<T> type) throws IOException {
            return (T) XmlXStream.getInstance().fromXML(textValue, type);
        }
    }

    static class XStreamJsonWrapper extends CodecWrapper {

        @Override
        public CodecType getCodecType() {
            return CodecType.XStreamJson;
        }

        @Override
        public <T> String encode(T object) throws IOException {
            return JsonXStream.getInstance().toXML(object);
        }

        @Override
        public <T> T decode(String textValue, Class<T> type) throws IOException {
            return (T) JsonXStream.getInstance().fromXML(textValue, type);
        }
    }

    static class LegacyJacksonJsonWrapper extends CodecWrapper {

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
        public <T> T decode(String textValue, Class<T> type) throws IOException {
            return codec.readValue(type, textValue);
        }
    }

    static class JacksonJsonWrapper extends CodecWrapper {

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
        public <T> T decode(String textValue, Class<T> type) throws IOException {
            return codec.getJsonMapper().readValue(textValue, type);
        }
    }

    static class JacksonXmlWrapper extends CodecWrapper {

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
        public <T> T decode(String textValue, Class<T> type) throws IOException {
            return codec.getXmlMapper().readValue(textValue, type);
        }
    }
}
