package com.netflix.eureka.transport.base;

import java.net.InetSocketAddress;

import com.netflix.eureka.transport.Message;
import com.netflix.eureka.transport.MessageBroker;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractMessageBrokerBuilder<T extends Message, B extends AbstractMessageBrokerBuilder<T, B>> {

    protected final InetSocketAddress address;
    protected PipelineConfigurator<Message, Message> codecPipeline;

    public AbstractMessageBrokerBuilder(InetSocketAddress address) {
        this.address = address;
    }

    public B withCodecPipeline(PipelineConfigurator<Message, Message> codecPipeline) {
        this.codecPipeline = codecPipeline;
        return (B) this;
    }

    public abstract BaseMessageBrokerServer buildServer();

    public abstract Observable<MessageBroker> buildClient();
}
