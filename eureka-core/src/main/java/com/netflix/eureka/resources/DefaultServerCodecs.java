package com.netflix.eureka.resources;

import com.netflix.appinfo.EurekaAccept;
import com.netflix.discovery.converters.wrappers.CodecWrappers;
import com.netflix.discovery.converters.wrappers.EncoderDecoderWrapper;
import com.netflix.discovery.converters.wrappers.EncoderWrapper;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.registry.ResponseCache;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author David Liu
 */
@Singleton
public class DefaultServerCodecs implements ServerCodecs {

    protected final EncoderDecoderWrapper fullJsonCodec;
    protected final EncoderDecoderWrapper compactJsonCodec;

    protected final EncoderDecoderWrapper fullXmlCodec;
    protected final EncoderDecoderWrapper compactXmlCodec;

    private static EncoderDecoderWrapper getFullJson(EurekaServerConfig serverConfig) {
        EncoderDecoderWrapper codec = CodecWrappers.getCodec(serverConfig.getJsonCodecName());
        return codec == null ? CodecWrappers.getCodec(CodecWrappers.LegacyJacksonJson.class) : codec;
    }

    private static EncoderDecoderWrapper getFullXml(EurekaServerConfig serverConfig) {
        EncoderDecoderWrapper codec = CodecWrappers.getCodec(serverConfig.getXmlCodecName());
        return codec == null ? CodecWrappers.getCodec(CodecWrappers.XStreamXml.class) : codec;
    }

    @Inject
    public DefaultServerCodecs(EurekaServerConfig serverConfig) {
        this (
                getFullJson(serverConfig),
                CodecWrappers.getCodec(CodecWrappers.JacksonJsonMini.class),
                getFullXml(serverConfig),
                CodecWrappers.getCodec(CodecWrappers.JacksonXmlMini.class)
        );
    }

    protected DefaultServerCodecs(EncoderDecoderWrapper fullJsonCodec,
                                  EncoderDecoderWrapper compactJsonCodec,
                                  EncoderDecoderWrapper fullXmlCodec,
                                  EncoderDecoderWrapper compactXmlCodec) {
        this.fullJsonCodec = fullJsonCodec;
        this.compactJsonCodec = compactJsonCodec;
        this.fullXmlCodec = fullXmlCodec;
        this.compactXmlCodec = compactXmlCodec;
    }

    @Override
    public EncoderDecoderWrapper getFullJsonCodec() {
        return fullJsonCodec;
    }

    @Override
    public EncoderDecoderWrapper getCompactJsonCodec() {
        return compactJsonCodec;
    }

    @Override
    public EncoderDecoderWrapper getFullXmlCodec() {
        return fullXmlCodec;
    }

    @Override
    public EncoderDecoderWrapper getCompactXmlCodecr() {
        return compactXmlCodec;
    }

    @Override
    public EncoderWrapper getEncoder(ResponseCache.KeyType keyType, boolean compact) {
        switch (keyType) {
            case JSON:
                return compact ? compactJsonCodec : fullJsonCodec;
            case XML:
            default:
                return compact ? compactXmlCodec : fullXmlCodec;
        }
    }

    @Override
    public EncoderWrapper getEncoder(ResponseCache.KeyType keyType, EurekaAccept eurekaAccept) {
        switch (eurekaAccept) {
            case compact:
                return getEncoder(keyType, true);
            case full:
            default:
                return getEncoder(keyType, false);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        protected EncoderDecoderWrapper fullJsonCodec;
        protected EncoderDecoderWrapper compactJsonCodec;

        protected EncoderDecoderWrapper fullXmlCodec;
        protected EncoderDecoderWrapper compactXmlCodec;

        protected Builder() {}

        public Builder withFullJsonCodec(EncoderDecoderWrapper fullJsonCodec) {
            this.fullJsonCodec = fullJsonCodec;
            return this;
        }

        public Builder withCompactJsonCodec(EncoderDecoderWrapper compactJsonCodec) {
            this.compactJsonCodec = compactJsonCodec;
            return this;
        }

        public Builder withFullXmlCodec(EncoderDecoderWrapper fullXmlCodec) {
            this.fullXmlCodec = fullXmlCodec;
            return this;
        }

        public Builder withCompactXmlCodec(EncoderDecoderWrapper compactXmlEncoder) {
            this.compactXmlCodec = compactXmlEncoder;
            return this;
        }

        public Builder withEurekaServerConfig(EurekaServerConfig config) {
            fullJsonCodec = CodecWrappers.getCodec(config.getJsonCodecName());
            fullXmlCodec = CodecWrappers.getCodec(config.getXmlCodecName());
            return this;
        }

        public ServerCodecs build() {
            if (fullJsonCodec == null) {
                fullJsonCodec = CodecWrappers.getCodec(CodecWrappers.LegacyJacksonJson.class);
            }

            if (compactJsonCodec == null) {
                compactJsonCodec = CodecWrappers.getCodec(CodecWrappers.JacksonJsonMini.class);
            }

            if (fullXmlCodec == null) {
                fullXmlCodec = CodecWrappers.getCodec(CodecWrappers.XStreamXml.class);
            }

            if (compactXmlCodec == null) {
                compactXmlCodec = CodecWrappers.getCodec(CodecWrappers.JacksonXmlMini.class);
            }

            return new DefaultServerCodecs(
                    fullJsonCodec,
                    compactJsonCodec,
                    fullXmlCodec,
                    compactXmlCodec
            );
        }
    }
}
