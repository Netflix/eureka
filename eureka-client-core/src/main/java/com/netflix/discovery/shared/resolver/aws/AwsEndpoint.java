package com.netflix.discovery.shared.resolver.aws;

import com.netflix.discovery.shared.resolver.DefaultEndpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author David Liu
 */
public class AwsEndpoint extends DefaultEndpoint {

    protected final String zone;
    protected final String region;

    public AwsEndpoint(String serviceURI, String region, String zone) {
        super(serviceURI);
        this.region = region;
        this.zone = zone;
    }

    public AwsEndpoint(String hostName, int port, boolean isSecure, String relativeUri, String region, String zone) {
        super(hostName, port, isSecure, relativeUri);
        this.region=region;
        this.zone=zone;
    }

    public String getRegion(){
        return region;
    }

    public String getZone(){
        return zone;
    }

    public static List<AwsEndpoint> createForServerList(
            List<String> hostNames, int port, boolean isSecure, String relativeUri, String region,String zone) {
        if (hostNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<AwsEndpoint> awsEndpoints = new ArrayList<>(hostNames.size());
        for (String hostName : hostNames) {
            awsEndpoints.add(new AwsEndpoint(hostName, port, isSecure, relativeUri, region, zone));
        }
        return awsEndpoints;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AwsEndpoint)) return false;
        if (!super.equals(o)) return false;

        AwsEndpoint that = (AwsEndpoint) o;

        if (region != null ? !region.equals(that.region) : that.region != null) return false;
        if (zone != null ? !zone.equals(that.zone) : that.zone != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (zone != null ? zone.hashCode() : 0);
        result = 31 * result + (region != null ? region.hashCode() : 0);
        return result;
    }

    @Override
    public String toString(){
        return"AwsEndpoint{ serviceUrl='"+serviceUrl+'\''
                +", region='"+region+'\''
                +", zone='"+zone+'\''+'}';
    }
}