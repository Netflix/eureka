package com.netflix.eureka2.transport;

import com.netflix.eureka2.transport.codec.AbstractEurekaCodec;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import rx.functions.Func1;

/**
 * @author David Liu
 */
public class EurekaPipelineConfigurator implements PipelineConfigurator<Object, Object> {

    private static final int MAX_FRAME_LENGTH = 65536;
    private final Func1<Codec, AbstractEurekaCodec> codecBuilder;
    private final Codec codec;

    public EurekaPipelineConfigurator(Func1<Codec, AbstractEurekaCodec> codecBuilder, Codec codec) {
        this.codecBuilder = codecBuilder;
        this.codec = codec;
    }

    @Override
    public void configureNewPipeline(ChannelPipeline pipeline) {
        pipeline.addLast(LengthFieldBasedFrameDecoder.class.getSimpleName(), new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4));
        pipeline.addLast(LengthFieldPrepender.class.getSimpleName(), new LengthFieldPrepender(4));

        AbstractEurekaCodec handler = codecBuilder.call(codec);
        pipeline.addLast(handler.getClass().getSimpleName(), handler);
    }
}
