package com.netflix.eureka2.testkit.cli.command.connect;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.testkit.cli.ClusterTopology;
import com.netflix.eureka2.testkit.cli.Context;
import com.netflix.eureka2.testkit.cli.Session;
import com.netflix.eureka2.testkit.cli.SessionDescriptor;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class ConnectCanonicalCommand extends AbstractConnectClusterCommand {
    public ConnectCanonicalCommand() {
        super("connect-canonical");
    }

    @Override
    public String getDescription() {
        return "connect to Eureka write cluster";
    }

    protected Observable<Session> createSession(Context context, ClusterTopology clusterTopology, int nodeIdx) {
        if (clusterTopology.getWriteServers().isEmpty()) {
            return Observable.error(new IllegalArgumentException("no write cluster nodes"));
        }
        if (clusterTopology.getReadClusterVip() == null) {
            return Observable.error(new IllegalArgumentException("read cluster VIP not defined"));
        }
        EurekaRegistrationClient registrationClient = Eurekas.newRegistrationClientBuilder()
                .withClientId("registrationWriteClusterClient")
                .withServerResolver(registrationResolverOf(clusterTopology.getWriteServers()))
                .build();

        ServerResolver readClusterResolver = ServerResolvers
                .fromEureka(interestResolverOf(clusterTopology.getWriteServers()))
                .forInterest(Interests.forVips(clusterTopology.getReadClusterVip()));

        EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withClientId("interestWriteClusterClient")
                .withServerResolver(readClusterResolver)
                .build();
        return Observable.just(new Session(SessionDescriptor.canonical(), registrationClient, interestClient));
    }
}
