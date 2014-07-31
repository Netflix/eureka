package com.netflix.eureka.transport.codec.json;

import com.netflix.eureka.transport.Message;
import io.netty.channel.ChannelPipeline;
import io.reactivex.netty.pipeline.PipelineConfigurator;

/**
 * @author Tomasz Bak
 */
public class JsonPipelineConfigurator implements PipelineConfigurator<Message, Message> {
    @Override
    public void configureNewPipeline(ChannelPipeline pipeline) {
        pipeline.addLast(new JsonCodec());
    }
}
