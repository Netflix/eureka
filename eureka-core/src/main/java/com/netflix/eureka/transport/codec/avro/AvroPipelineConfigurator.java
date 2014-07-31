package com.netflix.eureka.transport.codec.avro;

import java.util.Arrays;

import com.netflix.eureka.transport.Message;
import io.netty.channel.ChannelPipeline;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import org.apache.avro.Schema;

/**
 * @author Tomasz Bak
 */
public class AvroPipelineConfigurator implements PipelineConfigurator<Message, Message> {

    private final Schema schema;

    public AvroPipelineConfigurator(Class<?>... types) {
        schema = MessageBrokerSchema.brokerSchemaFrom(Arrays.asList(types));
    }

    @Override
    public void configureNewPipeline(ChannelPipeline pipeline) {
        pipeline.addLast(new AvroCodec(schema));
    }
}
