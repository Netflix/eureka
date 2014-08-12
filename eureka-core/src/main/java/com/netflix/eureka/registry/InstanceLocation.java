package com.netflix.eureka.registry;

import java.io.Serializable;

/**
 * @author David Liu
 */
public class InstanceLocation implements Serializable {

    // TODO: genericize
    private String datacenter;
    private String region;
    private String zone;

    public String getDatacenter() {
        return datacenter;
    }

    public void setDatacenter(String datacenter) {
        this.datacenter = datacenter;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InstanceLocation)) return false;

        InstanceLocation that = (InstanceLocation) o;

        if (datacenter != null ? !datacenter.equals(that.datacenter) : that.datacenter != null) return false;
        if (region != null ? !region.equals(that.region) : that.region != null) return false;
        if (zone != null ? !zone.equals(that.zone) : that.zone != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = datacenter != null ? datacenter.hashCode() : 0;
        result = 31 * result + (region != null ? region.hashCode() : 0);
        result = 31 * result + (zone != null ? zone.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "InstanceLocation{" +
                "datacenter='" + datacenter + '\'' +
                ", region='" + region + '\'' +
                ", zone='" + zone + '\'' +
                '}';
    }


    public static final class AmazonBuilder {
        private String region;
        private String zone;

        public AmazonBuilder() {
        }

        public AmazonBuilder withRegion(String region) {
            this.region = region;
            return this;
        }

        public AmazonBuilder withZone(String zone) {
            this.zone = zone;
            return this;
        }

        public InstanceLocation build() {
            InstanceLocation instanceLocation = new InstanceLocation();
            instanceLocation.setDatacenter("amazon");
            instanceLocation.setRegion(region);
            instanceLocation.setZone(zone);
            return instanceLocation;
        }
    }
}
