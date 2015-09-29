package com.netflix.eureka.resources;

import com.netflix.appinfo.EurekaAccept;
import com.netflix.discovery.converters.wrappers.CodecWrappers;
import com.netflix.discovery.converters.wrappers.CodecWrappers.JacksonJsonMini;
import com.netflix.discovery.converters.wrappers.CodecWrappers.JacksonXmlMini;
import com.netflix.discovery.converters.wrappers.CodecWrappers.LegacyJacksonJson;
import com.netflix.discovery.converters.wrappers.CodecWrappers.XStreamXml;
import com.netflix.discovery.converters.wrappers.EncoderWrapper;
import com.netflix.eureka.EurekaServerConfig;

/**
 * @author David Liu
 */
public class ServerCodecs {

    protected final EncoderWrapper fullJsonEncoder;
    protected final EncoderWrapper compactJsonEncoder;

    protected final EncoderWrapper fullXmlEncoder;
    protected final EncoderWrapper compactXmlEncoder;

    protected ServerCodecs(EncoderWrapper fullJsonEncoder,
                         EncoderWrapper compactJsonEncoder,
                         EncoderWrapper fullXmlEncoder,
                         EncoderWrapper compactXmlEncoder) {
        this.fullJsonEncoder = fullJsonEncoder;
        this.compactJsonEncoder = compactJsonEncoder;
        this.fullXmlEncoder = fullXmlEncoder;
        this.compactXmlEncoder = compactXmlEncoder;
    }

    public EncoderWrapper getEncoder(ResponseCache.KeyType keyType, boolean compact) {
        switch (keyType) {
            case JSON:
                return compact ? compactJsonEncoder : fullJsonEncoder;
            case XML:
            default:
                return compact ? compactXmlEncoder : fullXmlEncoder;
        }
    }

    public EncoderWrapper getEncoder(ResponseCache.KeyType keyType, EurekaAccept eurekaAccept) {
        switch (eurekaAccept) {
            case compact:
                return getEncoder(keyType, true);
            case full:
            default:
                return getEncoder(keyType, false);
        }
    }

    public static class Builder {
        private EncoderWrapper fullJsonEncoder;
        private EncoderWrapper compactJsonEncoder;

        private EncoderWrapper fullXmlEncoder;
        private EncoderWrapper compactXmlEncoder;

        public Builder withFullJsonEncoder(EncoderWrapper fullJsonEncoder) {
            this.fullJsonEncoder = fullJsonEncoder;
            return this;
        }

        public Builder withCompactJsonEncoder(EncoderWrapper compactJsonEncoder) {
            this.compactJsonEncoder = compactJsonEncoder;
            return this;
        }

        public Builder withFullXmlnEncoder(EncoderWrapper fullXmlEncoder) {
            this.fullXmlEncoder = fullXmlEncoder;
            return this;
        }

        public Builder withCompactXmlEncoder(EncoderWrapper compactXmlEncoder) {
            this.compactXmlEncoder = compactXmlEncoder;
            return this;
        }

        public Builder withEurekaServerConfig(EurekaServerConfig config) {
            fullJsonEncoder = CodecWrappers.getEncoder(config.getJsonCodecName());
            fullXmlEncoder = CodecWrappers.getEncoder(config.getXmlCodecName());
            return this;
        }

        public ServerCodecs build() {
            if (fullJsonEncoder == null) {
                fullJsonEncoder = CodecWrappers.getEncoder(LegacyJacksonJson.class);
            }

            if (compactJsonEncoder == null) {
                compactJsonEncoder = CodecWrappers.getEncoder(JacksonJsonMini.class);
            }

            if (fullXmlEncoder == null) {
                fullXmlEncoder = CodecWrappers.getEncoder(XStreamXml.class);
            }

            if (compactXmlEncoder == null) {
                compactXmlEncoder = CodecWrappers.getEncoder(JacksonXmlMini.class);
            }

            return new ServerCodecs(
                    fullJsonEncoder,
                    compactJsonEncoder,
                    fullXmlEncoder,
                    compactXmlEncoder
            );
        }
    }
}
