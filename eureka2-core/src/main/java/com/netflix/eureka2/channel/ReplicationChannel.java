package com.netflix.eureka2.channel;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Sourced;
import com.netflix.eureka2.model.instance.InstanceInfo;
import rx.Observable;

/**
 * A {@link com.netflix.eureka2.channel.ServiceChannel} implementation representing a replication stream
 * between two Eureka write servers.
 *
 * The client side of the channel is a source of data, that comes from its own registry, and is limited
 * to entries with {@link Source.Origin#LOCAL}.
 *
 * On the server side the data are put into the registry with origin set to {@link Source.Origin#REPLICATED}.
 * A replicated entry is removed from the registry under following circumstances:
 * <ul>
 *     <li>Explicit {@link #replicate(com.netflix.eureka2.model.notification.ChangeNotification)} call with a Kind.Delete</li>
 *     <li>Replication connection termination - invalidates all entries replicated over this connection</li>
 *     <li>No heartbeat within configured period of time - equivalent to connection termination</li>
 * </ul>
 *
 * @author Nitesh Kant
 */
public interface ReplicationChannel extends ServiceChannel, Sourced {

    enum STATE {Idle, Handshake, Connected, Closed}

    /**
     * Handshake message exchange. A client sends first {@link ReplicationHello} message, which
     * is followed by the {@link ReplicationHelloReply} from the server.
     *
     * @param hello initial message from the client
     * @return reply message from the server
     */
    Observable<ReplicationHelloReply> hello(ReplicationHello hello);

    /**
     * Submit a change notification to be replicated to a remote server
     *
     * @param notification the change notification to be replicated
     * @return Acknowledgement of the replication event
     */
    Observable<Void> replicate(ChangeNotification<InstanceInfo> notification);
}
