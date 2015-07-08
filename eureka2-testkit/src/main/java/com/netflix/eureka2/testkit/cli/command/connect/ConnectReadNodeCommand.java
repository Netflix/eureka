package com.netflix.eureka2.testkit.cli.command.connect;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.server.resolver.ClusterAddress;
import com.netflix.eureka2.testkit.cli.ClusterTopology;
import com.netflix.eureka2.testkit.cli.Context;
import com.netflix.eureka2.testkit.cli.Session;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class ConnectReadNodeCommand extends AbstractConnectClusterCommand {
    public ConnectReadNodeCommand() {
        super("connect-read-node", 1);
    }

    @Override
    public String getDescription() {
        return "connect to a given Eureka read node";
    }

    @Override
    public String getInvocationSyntax() {
        return getName() + " <read_node_idx>";
    }

    @Override
    protected Observable<Session> createSession(Context context, ClusterTopology clusterTopology, int nodeIdx) {
        if (clusterTopology.getReadServers().isEmpty()) {
            return Observable.error(new IllegalArgumentException("no read cluster nodes"));
        }
        if (nodeIdx < 0 || nodeIdx >= clusterTopology.getReadServers().size()) {
            return Observable.error(new IllegalArgumentException("node id out of range"));
        }
        ClusterAddress readNodeAddress = clusterTopology.getReadServers().get(nodeIdx);
        EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withClientId("interestReadServerClient")
                .withServerResolver(ServerResolvers.fromHostname(readNodeAddress.getHostName()).withPort(readNodeAddress.getInterestPort()))
                .build();
        return Observable.just(new Session(context, null, interestClient));
    }
}
