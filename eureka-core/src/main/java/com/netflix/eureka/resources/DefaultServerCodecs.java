package com.netflix.eureka.resources;

import com.netflix.appinfo.EurekaAccept;
import com.netflix.discovery.converters.wrappers.CodecWrapper;
import com.netflix.discovery.converters.wrappers.CodecWrappers;
import com.netflix.discovery.converters.wrappers.EncoderWrapper;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.registry.Key;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author David Liu
 */
@Singleton
public class DefaultServerCodecs implements ServerCodecs {

    protected final CodecWrapper fullJsonCodec;
    protected final CodecWrapper compactJsonCodec;

    protected final CodecWrapper fullXmlCodec;
    protected final CodecWrapper compactXmlCodec;

    private static CodecWrapper getFullJson(EurekaServerConfig serverConfig) {
        CodecWrapper codec = CodecWrappers.getCodec(serverConfig.getJsonCodecName());
        return codec == null ? CodecWrappers.getCodec(CodecWrappers.LegacyJacksonJson.class) : codec;
    }

    private static CodecWrapper getFullXml(EurekaServerConfig serverConfig) {
        CodecWrapper codec = CodecWrappers.getCodec(serverConfig.getXmlCodecName());
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

    protected DefaultServerCodecs(CodecWrapper fullJsonCodec,
                                  CodecWrapper compactJsonCodec,
                                  CodecWrapper fullXmlCodec,
                                  CodecWrapper compactXmlCodec) {
        this.fullJsonCodec = fullJsonCodec;
        this.compactJsonCodec = compactJsonCodec;
        this.fullXmlCodec = fullXmlCodec;
        this.compactXmlCodec = compactXmlCodec;
    }

    @Override
    public CodecWrapper getFullJsonCodec() {
        return fullJsonCodec;
    }

    @Override
    public CodecWrapper getCompactJsonCodec() {
        return compactJsonCodec;
    }

    @Override
    public CodecWrapper getFullXmlCodec() {
        return fullXmlCodec;
    }

    @Override
    public CodecWrapper getCompactXmlCodecr() {
        return compactXmlCodec;
    }

    @Override
    public EncoderWrapper getEncoder(Key.KeyType keyType, boolean compact) {
        switch (keyType) {
            case JSON:
                return compact ? compactJsonCodec : fullJsonCodec;
            case XML:
            default:
                return compact ? compactXmlCodec : fullXmlCodec;
        }
    }

    @Override
    public EncoderWrapper getEncoder(Key.KeyType keyType, EurekaAccept eurekaAccept) {
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
        protected CodecWrapper fullJsonCodec;
        protected CodecWrapper compactJsonCodec;

        protected CodecWrapper fullXmlCodec;
        protected CodecWrapper compactXmlCodec;

        protected Builder() {}

        public Builder withFullJsonCodec(CodecWrapper fullJsonCodec) {
            this.fullJsonCodec = fullJsonCodec;
            return this;
        }

        public Builder withCompactJsonCodec(CodecWrapper compactJsonCodec) {
            this.compactJsonCodec = compactJsonCodec;
            return this;
        }

        public Builder withFullXmlCodec(CodecWrapper fullXmlCodec) {
            this.fullXmlCodec = fullXmlCodec;
            return this;
        }

        public Builder withCompactXmlCodec(CodecWrapper compactXmlEncoder) {
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
