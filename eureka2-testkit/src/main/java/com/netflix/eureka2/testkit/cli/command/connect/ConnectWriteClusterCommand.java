package com.netflix.eureka2.testkit.cli.command.connect;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.testkit.cli.ClusterTopology;
import com.netflix.eureka2.testkit.cli.Context;
import com.netflix.eureka2.testkit.cli.Session;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class ConnectWriteClusterCommand extends AbstractConnectClusterCommand {
    public ConnectWriteClusterCommand() {
        super("connect-write-cluster");
    }

    @Override
    public String getDescription() {
        return "connect to Eureka write cluster";
    }

    protected Observable<Session> createSession(Context context, ClusterTopology clusterTopology, int nodeIdx) {
        if (clusterTopology.getWriteServers().isEmpty()) {
            return Observable.error(new IllegalArgumentException("no write cluster nodes"));
        }
        EurekaRegistrationClient registrationClient = Eurekas.newRegistrationClientBuilder()
                .withClientId("registrationWriteClusterClient")
                .withServerResolver(registrationResolverOf(clusterTopology.getWriteServers()))
                .build();
        EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withClientId("interestWriteClusterClient")
                .withServerResolver(interestResolverOf(clusterTopology.getWriteServers()))
                .build();
        return Observable.just(new Session(context, registrationClient, interestClient));
    }
}
