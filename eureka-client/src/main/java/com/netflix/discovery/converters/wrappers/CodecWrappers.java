package com.netflix.discovery.converters.wrappers;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.appinfo.EurekaAccept;
import com.netflix.discovery.converters.EurekaJacksonCodec;
import com.netflix.discovery.converters.JsonXStream;
import com.netflix.discovery.converters.KeyFormatter;
import com.netflix.discovery.converters.XmlXStream;
import com.netflix.discovery.converters.jackson.EurekaJsonJacksonCodec;
import com.netflix.discovery.converters.jackson.EurekaXmlJacksonCodec;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClientImpl;

/**
 * This is just a helper class during transition when multiple codecs are supported. One day this should all go away
 * when there is only 1 type of json and xml codecs each.
 *
 * For adding custom codecs to Discovery, prefer creating a custom EurekaJerseyClient to added to DiscoveryClient
 * either completely independently or via
 * {@link EurekaJerseyClientImpl.EurekaJerseyClientBuilder#withDecoderWrapper(DecoderWrapper)}
 * and
 * {@link EurekaJerseyClientImpl.EurekaJerseyClientBuilder#withEncoderWrapper(EncoderWrapper)}
 *
 * @author David Liu
 */
public final class CodecWrappers {

    private static final Map<String, CodecWrapper> CODECS = new ConcurrentHashMap<>();

    /**
     * For transition use: register a new codec wrapper.
     */
    public static void registerWrapper(CodecWrapper wrapper) {
        CODECS.put(wrapper.codecName(), wrapper);
    }

    public static <T extends CodecWrapperBase> String getCodecName(Class<T> clazz) {
        return clazz.getSimpleName();
    }

    public static <T extends CodecWrapper> CodecWrapper getCodec(Class<T> clazz) {
        return getCodec(getCodecName(clazz));
    }

    public static synchronized CodecWrapper getCodec(String name) {
        if (name == null) {
            return null;
        }

        if (!CODECS.containsKey(name)) {
            CodecWrapper wrapper = create(name);
            if (wrapper != null) {
                CODECS.put(wrapper.codecName(), wrapper);
            }
        }

        return CODECS.get(name);
    }

    public static <T extends EncoderWrapper> EncoderWrapper getEncoder(Class<T> clazz) {
        return getEncoder(getCodecName(clazz));
    }

    public static synchronized EncoderWrapper getEncoder(String name) {
        if (name == null) {
            return null;
        }

        if (!CODECS.containsKey(name)) {
            CodecWrapper wrapper = create(name);
            if (wrapper != null) {
                CODECS.put(wrapper.codecName(), wrapper);
            }
        }

        return CODECS.get(name);
    }

    public static <T extends DecoderWrapper> DecoderWrapper getDecoder(Class<T> clazz) {
        return getDecoder(getCodecName(clazz));
    }

    /**
     * Resolve the decoder to use based on the specified decoder name, as well as the specified eurekaAccept.
     * The eurekAccept trumps the decoder name if the decoder specified is one that is not valid for use for the
     * specified eurekaAccept.
     */
    public static synchronized DecoderWrapper resolveDecoder(String name, String eurekaAccept) {
        EurekaAccept accept = EurekaAccept.fromString(eurekaAccept);
        switch (accept) {
            case compact:
                return getDecoder(JacksonJsonMini.class);
            case full:
            default:
                return getDecoder(name);
        }
    }

    public static synchronized DecoderWrapper getDecoder(String name) {
        if (name == null) {
            return null;
        }

        if (!CODECS.containsKey(name)) {
            CodecWrapper wrapper = create(name);
            if (wrapper != null) {
                CODECS.put(wrapper.codecName(), wrapper);
            }
        }

        return CODECS.get(name);
    }

    private static CodecWrapper create(String name) {
        if (getCodecName(JacksonJson.class).equals(name)) {
            return new JacksonJson();
        } else if (getCodecName(JacksonJsonMini.class).equals(name)) {
            return new JacksonJsonMini();
        } else if (getCodecName(LegacyJacksonJson.class).equals(name)) {
            return new LegacyJacksonJson();
        } else if (getCodecName(XStreamJson.class).equals(name)) {
            return new XStreamJson();
        } else if (getCodecName(JacksonXml.class).equals(name)) {
            return new JacksonXml();
        } else if (getCodecName(JacksonXmlMini.class).equals(name)) {
            return new JacksonXmlMini();
        } else if (getCodecName(XStreamXml.class).equals(name)) {
            return new XStreamXml();
        } else {
            return null;
        }
    }

    // ========================
    // wrapper definitions
    // ========================

    public static class JacksonJson implements CodecWrapper {

        protected final EurekaJsonJacksonCodec codec = new EurekaJsonJacksonCodec();

        @Override
        public String codecName() {
            return getCodecName(this.getClass());
        }

        @Override
        public boolean support(MediaType mediaType) {
            return mediaType.equals(MediaType.APPLICATION_JSON_TYPE);
        }

        @Override
        public <T> String encode(T object) throws IOException {
            return codec.getObjectMapper(object.getClass()).writeValueAsString(object);
        }

        @Override
        public <T> void encode(T object, OutputStream outputStream) throws IOException {
            codec.writeTo(object, outputStream);
        }

        @Override
        public <T> T decode(String textValue, Class<T> type) throws IOException {
            return codec.getObjectMapper(type).readValue(textValue, type);
        }

        @Override
        public <T> T decode(InputStream inputStream, Class<T> type) throws IOException {
            return codec.getObjectMapper(type).readValue(inputStream, type);
        }
    }

    public static class JacksonJsonMini implements CodecWrapper {

        protected final EurekaJsonJacksonCodec codec = new EurekaJsonJacksonCodec(KeyFormatter.defaultKeyFormatter(), true);

        @Override
        public String codecName() {
            return getCodecName(this.getClass());
        }

        @Override
        public boolean support(MediaType mediaType) {
            return mediaType.equals(MediaType.APPLICATION_JSON_TYPE);
        }

        @Override
        public <T> String encode(T object) throws IOException {
            return codec.getObjectMapper(object.getClass()).writeValueAsString(object);
        }

        @Override
        public <T> void encode(T object, OutputStream outputStream) throws IOException {
            codec.writeTo(object, outputStream);
        }

        @Override
        public <T> T decode(String textValue, Class<T> type) throws IOException {
            return codec.getObjectMapper(type).readValue(textValue, type);
        }

        @Override
        public <T> T decode(InputStream inputStream, Class<T> type) throws IOException {
            return codec.getObjectMapper(type).readValue(inputStream, type);
        }
    }

    public static class JacksonXml implements CodecWrapper {

        protected final EurekaXmlJacksonCodec codec = new EurekaXmlJacksonCodec();

        @Override
        public String codecName() {
            return getCodecName(this.getClass());
        }

        @Override
        public boolean support(MediaType mediaType) {
            return mediaType.equals(MediaType.APPLICATION_XML_TYPE);
        }

        @Override
        public <T> String encode(T object) throws IOException {
            return codec.getObjectMapper(object.getClass()).writeValueAsString(object);
        }

        @Override
        public <T> void encode(T object, OutputStream outputStream) throws IOException {
            codec.writeTo(object, outputStream);
        }

        @Override
        public <T> T decode(String textValue, Class<T> type) throws IOException {
            return codec.getObjectMapper(type).readValue(textValue, type);
        }

        @Override
        public <T> T decode(InputStream inputStream, Class<T> type) throws IOException {
            return codec.getObjectMapper(type).readValue(inputStream, type);
        }
    }

    public static class JacksonXmlMini implements CodecWrapper {

        protected final EurekaXmlJacksonCodec codec = new EurekaXmlJacksonCodec(KeyFormatter.defaultKeyFormatter(), true);

        @Override
        public String codecName() {
            return getCodecName(this.getClass());
        }

        @Override
        public boolean support(MediaType mediaType) {
            return mediaType.equals(MediaType.APPLICATION_XML_TYPE);
        }

        @Override
        public <T> String encode(T object) throws IOException {
            return codec.getObjectMapper(object.getClass()).writeValueAsString(object);
        }

        @Override
        public <T> void encode(T object, OutputStream outputStream) throws IOException {
            codec.writeTo(object, outputStream);
        }

        @Override
        public <T> T decode(String textValue, Class<T> type) throws IOException {
            return codec.getObjectMapper(type).readValue(textValue, type);
        }

        @Override
        public <T> T decode(InputStream inputStream, Class<T> type) throws IOException {
            return codec.getObjectMapper(type).readValue(inputStream, type);
        }
    }

    public static class LegacyJacksonJson implements CodecWrapper {

        protected final EurekaJacksonCodec codec = new EurekaJacksonCodec();

        @Override
        public String codecName() {
            return getCodecName(this.getClass());
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

    public static class XStreamJson implements CodecWrapper {

        protected final JsonXStream codec = JsonXStream.getInstance();

        @Override
        public String codecName() {
            return getCodecName(this.getClass());
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
    public static class XStreamXml implements CodecWrapper {

        protected final XmlXStream codec = XmlXStream.getInstance();

        @Override
        public String codecName() {
            return CodecWrappers.getCodecName(this.getClass());
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
