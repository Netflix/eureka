package com.netflix.eureka.resources;

import com.netflix.appinfo.EurekaAccept;
import com.netflix.discovery.converters.wrappers.EncoderDecoderWrapper;
import com.netflix.discovery.converters.wrappers.EncoderWrapper;
import com.netflix.eureka.registry.ResponseCache;

/**
 * @author David Liu
 */
public interface ServerCodecs {

    EncoderDecoderWrapper getFullJsonCodec();

    EncoderDecoderWrapper getCompactJsonCodec();

    EncoderDecoderWrapper getFullXmlCodec();

    EncoderDecoderWrapper getCompactXmlCodecr();

    EncoderWrapper getEncoder(ResponseCache.KeyType keyType, boolean compact);

    EncoderWrapper getEncoder(ResponseCache.KeyType keyType, EurekaAccept eurekaAccept);
}
