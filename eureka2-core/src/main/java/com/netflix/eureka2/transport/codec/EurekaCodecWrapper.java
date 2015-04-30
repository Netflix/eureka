package com.netflix.eureka2.transport.codec;

import java.util.List;

import com.netflix.eureka2.codec.EurekaCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author Tomasz Bak
 */
public class EurekaCodecWrapper extends AbstractNettyCodec {

    private final EurekaCodec<Object> eurekaCodec;

    public EurekaCodecWrapper(EurekaCodec<Object> eurekaCodec) {
        this.eurekaCodec = eurekaCodec;
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return eurekaCodec.accept(msg.getClass());
    }

    @Override
    public void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        ByteBufOutputStream byteBufOutputStream = new ByteBufOutputStream(out);
        eurekaCodec.encode(msg, byteBufOutputStream);
    }

    @Override
    public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        ByteBufInputStream byteBufInputStream = new ByteBufInputStream(in);
        out.add(eurekaCodec.decode(byteBufInputStream));
    }
}
