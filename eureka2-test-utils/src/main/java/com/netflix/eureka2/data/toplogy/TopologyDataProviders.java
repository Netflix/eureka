package com.netflix.eureka2.data.toplogy;

/**
 * @author Tomasz Bak
 */
public final class TopologyDataProviders {
    private TopologyDataProviders() {
    }

    public static ServiceTopologyGenerator serviceTopologyGeneratorFor(String topologyId, int registrationRate, int registrationDuration, int maxRegistrations) {
        // Number services in topology should be bigger than number of concurrent registrations, so we do not
        // run out of available InstanceInfo objects.
        // We set minimum to 100, so if the rate is low, we have some diversification over available applications.
        int topologySize = Math.max(100, (int) (registrationRate * registrationDuration / 1000 * 1.25));
        if (maxRegistrations > 0) {
            topologySize = Math.min(topologySize, (int) (maxRegistrations * 1.25));
        }

        return SampleServiceTopologies.Mixed.builder().withTopologyId(topologyId).scaledTo(topologySize).build();
    }
}
