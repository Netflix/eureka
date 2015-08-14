package com.netflix.eureka2.codec;

import java.util.Collections;
import java.util.Set;

import com.netflix.eureka2.codec.json.EurekaJsonCodec;
import com.netflix.eureka2.codec.json.MultiSourcedDataHolderJsonCodec;
import com.netflix.eureka2.registry.MultiSourcedDataHolder;
import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * A set of static methods that should be used by clients to access different types of codecs.
 * Instead of providing single codec supporting any Eureka type, type specific codes are provided. It is
 * because some codes may be based on underlying schema descriptors, that like for Avro may be split into
 * separate documents.
 *
 * @author Tomasz Bak
 */
public final class EurekaCodecs {

    private EurekaCodecs() {
    }

    public static EurekaCodec<InstanceInfo> getInstanceInfoCodec(CodecType codecType) {
        switch (codecType) {
            case Json:
                return new EurekaJsonCodec<>((Set) Collections.singleton(InstanceInfo.class));
        }
        throw new IllegalArgumentException(codecType + " not supported yet");
    }

    public static EurekaCodec<MultiSourcedDataHolder<InstanceInfo>> getMultiSourcedDataHolderCodec(CodecType codecType) {
        switch (codecType) {
            case Json:
                return new MultiSourcedDataHolderJsonCodec(false);
        }
        throw new IllegalArgumentException(codecType + " not supported yet");
    }

    public static EurekaCodec<MultiSourcedDataHolder<InstanceInfo>> getCompactMultiSourcedDataHolderCodec(CodecType codecType) {
        switch (codecType) {
            case Json:
                return new MultiSourcedDataHolderJsonCodec(true);
        }
        throw new IllegalArgumentException(codecType + " not supported yet");
    }
}
