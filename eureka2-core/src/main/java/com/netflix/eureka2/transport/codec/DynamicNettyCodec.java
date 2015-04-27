package com.netflix.eureka2.transport.codec;

import com.netflix.eureka2.transport.Acknowledgement;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An eurekaCodec that dynamically changes it's encoding version based on the received version it has last decoded.
 * This codec is designed for server-side use where connections are always client initiated and the server will encode
 * all responses with the same version as the client messages.
 *
 * An instance of this codec *cannot* be shared between multiple channels.
 *
 * @author David Liu
 */
public class DynamicNettyCodec extends AbstractNettyCodec {
    private final Set<Class<?>> protocolTypes;
    private final Map<Byte, AbstractNettyCodec> codecMap;

    private volatile AbstractNettyCodec curentCodec;
    private volatile byte currentCodecVersion;

    public DynamicNettyCodec(Set<Class<?>> protocolTypes, Map<Byte, AbstractNettyCodec> codecMap, byte defaultCodecVersion) {
        this.protocolTypes = protocolTypes;
        this.codecMap = codecMap;
        this.currentCodecVersion = defaultCodecVersion;
        this.curentCodec = codecMap.get(defaultCodecVersion);
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof Acknowledgement || protocolTypes.contains(msg.getClass());
    }

    @Override
    public void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        if (curentCodec == null) {
            throw new IllegalArgumentException("Codec not available for eureka protocol version " + currentCodecVersion);
        }

        out.writeByte(currentCodecVersion);
        curentCodec.encode(ctx, msg, out);
    }

    @Override
    public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Decode the first message
        int readableBytes = in.readableBytes();
        if (readableBytes == 0) {
            return;
        }

        currentCodecVersion = in.readByte();
        curentCodec = codecMap.get(currentCodecVersion);

        if (curentCodec == null) {
            throw new IllegalArgumentException("Codec not available for eureka protocol version " + currentCodecVersion);
        }

        curentCodec.decode(ctx, in, out);
    }
}
