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
class ServerCodecs {

    private final EncoderWrapper fullJsonEncoder;
    private final EncoderWrapper miniJsonEncoder;

    private final EncoderWrapper fullXmlEncoder;
    private final EncoderWrapper miniXmlEncoder;

    private static EncoderWrapper getFullJsonEncoderWrapper(String name) {
        EncoderWrapper temp = CodecWrappers.getEncoder(name);
        return temp == null ? CodecWrappers.getEncoder(LegacyJacksonJson.class) : temp;
    }

    private static EncoderWrapper getFullXmlEncoderWrapper(String name) {
        EncoderWrapper temp = CodecWrappers.getEncoder(name);
        return temp == null ? CodecWrappers.getEncoder(XStreamXml.class) : temp;
    }

    public ServerCodecs(EurekaServerConfig config) {
        this(
                getFullJsonEncoderWrapper(config.getJsonCodecName()),
                CodecWrappers.getEncoder(JacksonJsonMini.class),
                getFullXmlEncoderWrapper(config.getXmlCodecName()),
                CodecWrappers.getEncoder(JacksonXmlMini.class)
        );
    }

    public ServerCodecs(EncoderWrapper fullJsonEncoder,
                        EncoderWrapper miniJsonEncoder,
                        EncoderWrapper fullXmlEncoder,
                        EncoderWrapper miniXmlEncoder) {
        this.fullJsonEncoder = fullJsonEncoder;
        this.miniJsonEncoder = miniJsonEncoder;
        this.fullXmlEncoder = fullXmlEncoder;
        this.miniXmlEncoder = miniXmlEncoder;
    }

    public EncoderWrapper getEncoder(ResponseCache.KeyType keyType, boolean compact) {
        switch (keyType) {
            case JSON:
                return compact ? miniJsonEncoder : fullJsonEncoder;
            case XML:
            default:
                return compact ? miniXmlEncoder : fullXmlEncoder;
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
}
