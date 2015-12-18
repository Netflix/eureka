package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.protocol.ProtocolModel;
import com.netflix.eureka2.spi.protocol.replication.ReplicationHello;
import com.netflix.eureka2.spi.protocol.replication.ReplicationHelloReply;

import static com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo.DiscoveryServer;
import static com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo.ZuulServer;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractReplicationChannelTest {

    protected static final String SENDER_ID = "senderId";

    protected static final String RECEIVER_ID = "receiverId";
    protected static final InstanceInfo RECEIVER_INFO = DiscoveryServer.builder().withId(RECEIVER_ID).build();

    protected static final Source SENDER_SOURCE = InstanceModel.getDefaultModel().createSource(Source.Origin.REPLICATED, SENDER_ID, 123);
    protected static final Source RECEIVER_SOURCE = InstanceModel.getDefaultModel().createSource(Source.Origin.REPLICATED, RECEIVER_ID, 123);

    protected static final ReplicationHello HELLO = ProtocolModel.getDefaultModel().newReplicationHello(SENDER_SOURCE, 0);
    protected static final ReplicationHelloReply HELLO_REPLY = ProtocolModel.getDefaultModel().newReplicationHelloReply(RECEIVER_SOURCE, false);

    protected static final InstanceInfo APP_INFO = ZuulServer.build();
}
