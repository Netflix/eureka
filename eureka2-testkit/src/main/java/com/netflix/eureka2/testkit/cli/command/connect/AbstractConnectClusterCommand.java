package com.netflix.eureka2.testkit.cli.command.connect;

import java.util.List;

import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.server.resolver.ClusterAddress;
import com.netflix.eureka2.testkit.cli.ClusterTopology;
import com.netflix.eureka2.testkit.cli.Command;
import com.netflix.eureka2.testkit.cli.Context;
import com.netflix.eureka2.testkit.cli.Session;
import rx.Notification;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractConnectClusterCommand extends Command {

    protected AbstractConnectClusterCommand(String name) {
        super(name);
    }

    protected AbstractConnectClusterCommand(String name, int paramCount) {
        super(name, paramCount);
    }

    @Override
    protected boolean executeCommand(final Context context, String[] args) {
        final int nodeIdx = args.length > 0 ? Integer.parseInt(args[0]) : -1;
        Notification<Session> result = context
                .clusterTopologies()
                .take(1)
                .flatMap(new Func1<ClusterTopology, Observable<Session>>() {
                    @Override
                    public Observable<Session> call(ClusterTopology clusterTopology) {
                        return createSession(context, clusterTopology, nodeIdx);
                    }
                })
                .materialize().toBlocking().first();
        if (result.isOnError()) {
            System.out.println("ERROR: " + result.getThrowable());
            result.getThrowable();
            return false;
        }
        context.setActiveSession(result.getValue());
        System.out.println("Connected");
        return true;
    }

    protected abstract Observable<Session> createSession(Context context, ClusterTopology clusterTopology, int nodeIdx);

    protected static ServerResolver registrationResolverOf(List<ClusterAddress> writeServers) {
        Server[] servers = new Server[writeServers.size()];
        for (int i = 0; i < servers.length; i++) {
            ClusterAddress clusterAddress = writeServers.get(i);
            servers[i] = new Server(clusterAddress.getHostName(), clusterAddress.getPort());
        }
        return ServerResolvers.from(servers);
    }

    protected static ServerResolver interestResolverOf(List<ClusterAddress> eurekaServers) {
        Server[] servers = new Server[eurekaServers.size()];
        for (int i = 0; i < servers.length; i++) {
            ClusterAddress clusterAddress = eurekaServers.get(i);
            servers[i] = new Server(clusterAddress.getHostName(), clusterAddress.getPort());
        }
        return ServerResolvers.from(servers);
    }
}
