package com.netflix.eureka2.transport.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import java.util.List;

/**
 * @author David Liu
 */
public abstract class AbstractNettyCodec extends ByteToMessageCodec<Object> {

    /**
     * Override for public access
     */
    @Override
    public abstract void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception;

    /**
     * Override for public access
     */
    @Override
    public abstract void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception;
}
