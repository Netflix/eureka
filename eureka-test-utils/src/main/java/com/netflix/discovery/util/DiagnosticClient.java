package com.netflix.discovery.util;

import com.netflix.discovery.shared.Applications;
import com.netflix.eureka.DefaultEurekaServerConfig;
import com.netflix.eureka.cluster.JerseyReplicationClient;

import static com.netflix.discovery.util.ApplicationFunctions.countInstances;

/**
 * A tool for running diagnostic tasks against a discovery server. Currently limited to observing
 * of consistency of delta updates.
 *
 * @author Tomasz Bak
 */
public class DiagnosticClient {

    public static void main(String[] args) throws InterruptedException {
        String discoveryURL = args[0];
        JerseyReplicationClient client = new JerseyReplicationClient(new DefaultEurekaServerConfig("eureka."), discoveryURL);
        Applications applications = client.getApplications().getEntity();
        System.out.println("Applications count=" + applications.getRegisteredApplications().size());
        System.out.println("Instance count=" + countInstances(applications));
        while (true) {
            Thread.sleep(30 * 1000);
            Applications delta = client.getDelta().getEntity();
            Applications merged = ApplicationFunctions.merge(applications, delta);
            if (merged.getAppsHashCode().equals(delta.getAppsHashCode())) {
                System.out.println("Hash codes match: " + delta.getAppsHashCode() + "(delta count=" + countInstances(delta) + ')');
                applications = merged;
            } else {
                System.out.println("ERROR: hash codes do not match (" + delta.getAppsHashCode() + "(delta) != "
                                + merged.getAppsHashCode() + " (merged) != "
                                + applications.getAppsHashCode() + "(old apps)" +
                                "(delta count=" + countInstances(delta) + ')'
                );
                applications = client.getApplications().getEntity();
            }
        }
    }
}
