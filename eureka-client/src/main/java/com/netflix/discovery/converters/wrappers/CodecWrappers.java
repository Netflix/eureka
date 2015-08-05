package com.netflix.discovery.converters.wrappers;

import com.netflix.discovery.converters.EurekaJacksonCodec;
import com.netflix.discovery.converters.JsonXStream;
import com.netflix.discovery.converters.KeyFormatter;
import com.netflix.discovery.converters.XmlXStream;
import com.netflix.discovery.converters.jackson.EurekaJacksonCodecNG;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 *
 * @author David Liu
 */
public final class CodecWrappers {

    private static final Map<String, EncoderDecoderWrapper> CODECS = new ConcurrentHashMap<>();

    public static synchronized EncoderWrapper getEncoder(String name) {
        if (name == null) {
            return null;
        }

        if (JacksonJsonMini.class.getSimpleName().equals(name)) {
            throw new UnsupportedOperationException("Encoder: " + name + "is not supported");
        }

        if (!CODECS.containsKey(name)) {
            EncoderDecoderWrapper wrapper = create(name);
            if (wrapper != null) {
                CODECS.put(wrapper.codecName(), wrapper);
            }
        }

        return CODECS.get(name);
    }

    public static synchronized DecoderWrapper getDecoder(String name) {
        if (name == null) {
            return null;
        }

        if (!CODECS.containsKey(name)) {
            EncoderDecoderWrapper wrapper = create(name);
            if (wrapper != null) {
                CODECS.put(wrapper.codecName(), wrapper);
            }
        }

        return CODECS.get(name);
    }

    private static EncoderDecoderWrapper create(String name) {
        if (JacksonJson.class.getSimpleName().equals(name)) {
            return new JacksonJson();
        } else if (JacksonJsonMini.class.getSimpleName().equals(name)) {
            return new JacksonJsonMini();
        } else if (LegacyJacksonJson.class.getSimpleName().equals(name)) {
            return new LegacyJacksonJson();
        } else if (XStreamJson.class.getSimpleName().equals(name)) {
            return new XStreamJson();
        } else if (JacksonXml.class.getSimpleName().equals(name)) {
            return new JacksonXml();
        } else if (XmlXStream.class.getSimpleName().equals(name)) {
            return new XStreamXml();
        } else {
            return null;
        }
    }

    // ========================
    // wrapper definitions
    // ========================

    public static class JacksonJson implements EncoderDecoderWrapper {

        protected final EurekaJacksonCodecNG codec = new EurekaJacksonCodecNG();

        @Override
        public String codecName() {
            return this.getClass().getSimpleName();
        }

        @Override
        public boolean support(MediaType mediaType) {
            return mediaType.equals(MediaType.APPLICATION_JSON_TYPE);
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

    public static class JacksonJsonMini implements EncoderDecoderWrapper {

        protected final EurekaJacksonCodecNG codec = new EurekaJacksonCodecNG(KeyFormatter.defaultKeyFormatter(), true);

        @Override
        public String codecName() {
            return this.getClass().getSimpleName();
        }

        @Override
        public boolean support(MediaType mediaType) {
            return mediaType.equals(MediaType.APPLICATION_JSON_TYPE);
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

    public static class JacksonXml implements EncoderDecoderWrapper {

        protected final EurekaJacksonCodecNG codec = new EurekaJacksonCodecNG();

        @Override
        public String codecName() {
            return this.getClass().getSimpleName();
        }

        @Override
        public boolean support(MediaType mediaType) {
            return mediaType.equals(MediaType.APPLICATION_XML_TYPE);
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

    public static class LegacyJacksonJson implements EncoderDecoderWrapper {

        protected final EurekaJacksonCodec codec = new EurekaJacksonCodec();

        @Override
        public String codecName() {
            return this.getClass().getSimpleName();
        }

        @Override
        public boolean support(MediaType mediaType) {
            return mediaType.equals(MediaType.APPLICATION_JSON_TYPE);
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

    public static class XStreamJson implements EncoderDecoderWrapper {

        protected final JsonXStream codec = JsonXStream.getInstance();

        @Override
        public String codecName() {
            return this.getClass().getSimpleName();
        }

        @Override
        public boolean support(MediaType mediaType) {
            return mediaType.equals(MediaType.APPLICATION_JSON_TYPE);
        }

        @Override
        public <T> String encode(T object) throws IOException {
            return codec.toXML(object);
        }

        @Override
        public <T> void encode(T object, OutputStream outputStream) throws IOException {
            codec.toXML(object, outputStream);
        }

        @Override
        public <T> T decode(String textValue, Class<T> type) throws IOException {
            return (T) codec.fromXML(textValue, type);
        }

        @Override
        public <T> T decode(InputStream inputStream, Class<T> type) throws IOException {
            return (T) codec.fromXML(inputStream, type);
        }
    }

    /**
     * @author David Liu
     */
    public static class XStreamXml implements EncoderDecoderWrapper {

        protected final XmlXStream codec = XmlXStream.getInstance();

        @Override
        public String codecName() {
            return this.getClass().getSimpleName();
        }

        @Override
        public boolean support(MediaType mediaType) {
            return mediaType.equals(MediaType.APPLICATION_XML_TYPE);
        }

        @Override
        public <T> String encode(T object) throws IOException {
            return codec.toXML(object);
        }

        @Override
        public <T> void encode(T object, OutputStream outputStream) throws IOException {
            codec.toXML(object, outputStream);
        }

        @Override
        public <T> T decode(String textValue, Class<T> type) throws IOException {
            return (T) codec.fromXML(textValue, type);
        }

        @Override
        public <T> T decode(InputStream inputStream, Class<T> type) throws IOException {
            return (T) codec.fromXML(inputStream, type);
        }
    }
}
