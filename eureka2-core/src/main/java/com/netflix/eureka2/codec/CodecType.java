package com.netflix.eureka2.codec;

/**
 * Breaking protocol/codec changes need to define additional codec versions here
 */
@Deprecated
public enum CodecType {
    Avro((byte) 1),
    Json((byte) 2);

    private final byte version;

    CodecType(byte version) {
        this.version = version;
    }

    public byte getVersion() {
        return version;
    }
}
