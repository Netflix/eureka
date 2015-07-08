package com.netflix.eureka2.testkit.cli.command.connect;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.testkit.cli.ClusterTopology;
import com.netflix.eureka2.testkit.cli.Context;
import com.netflix.eureka2.testkit.cli.Session;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class ConnectReadClusterCommand extends AbstractConnectClusterCommand {
    public ConnectReadClusterCommand() {
        super("connect-read-cluster");
    }

    @Override
    public String getDescription() {
        return "connect to Eureka read cluster";
    }

    protected Observable<Session> createSession(Context context, ClusterTopology clusterTopology, int nodeIdx) {
        if (clusterTopology.getReadServers().isEmpty()) {
            return Observable.error(new IllegalArgumentException("no read cluster nodes"));
        }
        EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withClientId("interestWriteClusterClient")
                .withServerResolver(interestResolverOf(clusterTopology.getReadServers()))
                .build();
        return Observable.just(new Session(context, null, interestClient));
    }
}
