package com.netflix.eureka2.testkit.cli.command.connect;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.server.resolver.ClusterAddress;
import com.netflix.eureka2.testkit.cli.ClusterTopology;
import com.netflix.eureka2.testkit.cli.Context;
import com.netflix.eureka2.testkit.cli.Session;
import com.netflix.eureka2.testkit.cli.SessionDescriptor;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class ConnectWriteNodeCommand extends AbstractConnectClusterCommand {
    public ConnectWriteNodeCommand() {
        super("connect-write-node", 1);
    }

    @Override
    public String getDescription() {
        return "connect to a given Eureka write node";
    }

    @Override
    public String getInvocationSyntax() {
        return getName() + " <write_node_idx>";
    }

    @Override
    protected Observable<Session> createSession(Context context, ClusterTopology clusterTopology, int nodeIdx) {
        if (clusterTopology.getWriteServers().isEmpty()) {
            return Observable.error(new IllegalArgumentException("no write cluster nodes"));
        }
        if (nodeIdx < 0 || nodeIdx >= clusterTopology.getWriteServers().size()) {
            return Observable.error(new IllegalArgumentException("node id out of range"));
        }
        ClusterAddress writeNodeAddress = clusterTopology.getWriteServers().get(nodeIdx);
        EurekaRegistrationClient registrationClient = Eurekas.newRegistrationClientBuilder()
                .withClientId("registrationWriteServerClient")
                .withServerResolver(ServerResolvers.fromHostname(writeNodeAddress.getHostName()).withPort(writeNodeAddress.getPort()))
                .build();
        EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withClientId("interestWriteServerClient")
                .withServerResolver(ServerResolvers.fromHostname(writeNodeAddress.getHostName()).withPort(writeNodeAddress.getPort()))
                .build();
        return Observable.just(new Session(SessionDescriptor.writeNode(nodeIdx), registrationClient, interestClient));
    }
}
