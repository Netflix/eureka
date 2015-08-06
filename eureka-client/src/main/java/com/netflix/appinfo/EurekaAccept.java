package com.netflix.appinfo;

import com.netflix.discovery.converters.wrappers.CodecWrappers;
import com.netflix.discovery.converters.wrappers.CodecWrappers.*;
import com.netflix.discovery.converters.wrappers.DecoderWrapper;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David Liu
 */
public enum EurekaAccept {
    full, mini;

    public static final String HTTP_X_EUREKA_ACCEPT = "X-Eureka-Accept";

    private static final Map<String, EurekaAccept> decoderNameToAcceptMap = new HashMap<>();
    static {
        decoderNameToAcceptMap.put(CodecWrappers.getCodecName(LegacyJacksonJson.class), full);
        decoderNameToAcceptMap.put(CodecWrappers.getCodecName(JacksonJson.class), full);
        decoderNameToAcceptMap.put(CodecWrappers.getCodecName(XStreamJson.class), full);
        decoderNameToAcceptMap.put(CodecWrappers.getCodecName(XStreamXml.class), full);
        decoderNameToAcceptMap.put(CodecWrappers.getCodecName(JacksonXml.class), full);

        decoderNameToAcceptMap.put(CodecWrappers.JacksonJsonMini.class.getSimpleName(), mini);
    }

    public static EurekaAccept getClientAccept(DecoderWrapper decoderWrapper) {
        return decoderNameToAcceptMap.get(decoderWrapper.codecName());
    }

    public static EurekaAccept fromString(String name) {
        if (name == null || name.isEmpty()) {
            return full;
        }

        try {
            return EurekaAccept.valueOf(name.toLowerCase());
        } catch (Exception e) {
            return full;
        }
    }
}
