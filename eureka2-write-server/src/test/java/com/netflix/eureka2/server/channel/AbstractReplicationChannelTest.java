package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.instance.InstanceInfo;

import static com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo.DiscoveryServer;
import static com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo.ZuulServer;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractReplicationChannelTest {
    protected static final String SENDER_ID = "senderId";

    protected static final String RECEIVER_ID = "receiverId";
    protected static final InstanceInfo RECEIVER_INFO = DiscoveryServer.builder().withId(RECEIVER_ID).build();

    protected static final Source SENDER_SOURCE = new Source(Source.Origin.REPLICATED, SENDER_ID, 123);
    protected static final Source RECEIVER_SOURCE = new Source(Source.Origin.REPLICATED, RECEIVER_ID,123);

    protected static final ReplicationHello HELLO = new ReplicationHello(SENDER_SOURCE, 0);
    protected static final ReplicationHelloReply HELLO_REPLY = new ReplicationHelloReply(RECEIVER_SOURCE, false);

    protected static final InstanceInfo APP_INFO = ZuulServer.build();
}
