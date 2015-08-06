package com.netflix.eureka.resources;

import com.netflix.appinfo.EurekaAccept;
import com.netflix.discovery.converters.wrappers.CodecWrappers;
import com.netflix.discovery.converters.wrappers.CodecWrappers.*;
import com.netflix.discovery.converters.wrappers.EncoderWrapper;
import com.netflix.eureka.EurekaServerConfig;

/**
 * @author David Liu
 */
class ServerCodecs {

    private final EncoderWrapper fullJsonEncoder;
    private final EncoderWrapper miniJsonEncoder;

    private final EncoderWrapper fullXmlEncoder;

    public ServerCodecs(EurekaServerConfig config) {
        EncoderWrapper temp = CodecWrappers.getEncoder(config.getJsonCodecName());
        fullJsonEncoder = temp == null ? CodecWrappers.getEncoder(LegacyJacksonJson.class) : temp;

        temp = CodecWrappers.getEncoder(config.getXmlCodecName());
        fullXmlEncoder = temp == null ? CodecWrappers.getEncoder(XStreamXml.class) : temp;

        miniJsonEncoder = CodecWrappers.getEncoder(JacksonJsonMini.class);
    }

    public EncoderWrapper getEncoder(ResponseCache.KeyType keyType) {
        switch (keyType) {
            case JSON:
                return fullJsonEncoder;
            case XML:
            default:
                return fullXmlEncoder;
        }
    }

    public EncoderWrapper getEncoder(ResponseCache.KeyType keyType, EurekaAccept eurekaAccept) {
        switch (eurekaAccept) {
            case compact:
                return miniJsonEncoder;
            case full:
            default:
                return getEncoder(keyType);
        }
    }
}
