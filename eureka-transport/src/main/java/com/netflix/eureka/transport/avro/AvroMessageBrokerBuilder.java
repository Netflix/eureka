package com.netflix.eureka.transport.avro;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.netflix.eureka.transport.Message;
import com.netflix.eureka.transport.MessageBroker;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import org.apache.avro.Schema;
import rx.Observable;
import rx.functions.Func1;

/**
 * AvroMessageBrokerBuilder generates Avro protocol specification from the provided configuration data, and
 * instantiates a {@link MessageBroker} that can automatically marshall/unmarshall registered user data types.
 *
 * @author Tomasz Bak
 */
public class AvroMessageBrokerBuilder {

    // We should set it to a reasonable level, so we rather fail
    // than accumulate data indefinitely.
    private static final int MAX_FRAME_LENGTH = 65536;

    private final InetSocketAddress address;
    private final List<Class<?>> types = new ArrayList<Class<?>>();

    public AvroMessageBrokerBuilder(InetSocketAddress address) {
        this.address = address;
    }

    public AvroMessageBrokerBuilder withTypes(Class<?>... types) {
        Collections.addAll(this.types, types);
        return this;
    }

    public AvroMessageBrokerServer buildServer() {
        return new AvroMessageBrokerServer(address.getPort(), createMessagePipelineConfigurator());
    }

    public Observable<MessageBroker> buildClient() {
        return RxNetty.<Message, Message>newTcpClientBuilder(address.getHostName(), address.getPort())
                .pipelineConfigurator(createMessagePipelineConfigurator())
                .enableWireLogging(LogLevel.ERROR)
                .build()
                .connect()
                .map(new Func1<ObservableConnection<Message, Message>, MessageBroker>() {
                    @Override
                    public MessageBroker call(ObservableConnection<Message, Message> observableConnection) {
                        return new AvroMessageBroker(observableConnection);
                    }
                });
    }

    private PipelineConfigurator<Message, Message> createMessagePipelineConfigurator() {
        return new AvroPipelineConfigurator(MessageBrokerSchema.brokerSchemaFrom(types));
    }

    static class AvroPipelineConfigurator implements PipelineConfigurator<Message, Message> {
        private final Schema schema;

        AvroPipelineConfigurator(Schema schema) {
            this.schema = schema;
        }

        @Override
        public void configureNewPipeline(ChannelPipeline pipeline) {
            pipeline.addLast(new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4));
            pipeline.addLast(new LengthFieldPrepender(4));
            pipeline.addLast(new AvroCodec(schema));
        }
    }
}
