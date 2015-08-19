package com.netflix.eureka2.server.rest.system;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;

/**
 * @author Tomasz Bak
 */
public class ApplicationDescriptor {

    private final String name;
    private final List<AsgDescriptor> asgs;
    private final Map<Status, Integer> instances;

    public ApplicationDescriptor(String name, List<AsgDescriptor> asgs, Map<Status, Integer> instances) {
        this.name = name;
        this.asgs = asgs;
        this.instances = instances;
    }

    public String getName() {
        return name;
    }

    public List<AsgDescriptor> getAsgs() {
        return asgs;
    }

    public Map<Status, Integer> getInstances() {
        return instances;
    }

    public static Builder anApplicationDescriptor(String name) {
        return new Builder(name);
    }

    public static class AsgDescriptor {
        private final String name;
        private final int size;

        public AsgDescriptor(String name, int size) {
            this.name = name;
            this.size = size;
        }

        public String getName() {
            return name;
        }

        public int getSize() {
            return size;
        }
    }

    public static class Builder {

        private final String name;
        private final Map<String, Integer> asgCounts = new HashMap<>();
        private final Map<Status, Integer> statusCounts = new HashMap<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder with(InstanceInfo instanceInfo) {
            String asgName = instanceInfo.getAsg();
            if (asgName == null) {
                asgName = "undefined";
            }
            Integer count = asgCounts.get(asgName);
            asgCounts.put(asgName, count == null ? 1 : count + 1);
            Status status = instanceInfo.getStatus();
            if (status != null) {
                count = statusCounts.get(status);
                statusCounts.put(status, count == null ? 1 : count + 1);
            }
            return this;
        }

        public ApplicationDescriptor build() {
            List<AsgDescriptor> asgs = new ArrayList<>(asgCounts.size());
            for (Map.Entry<String, Integer> entry : asgCounts.entrySet()) {
                asgs.add(new AsgDescriptor(entry.getKey(), entry.getValue()));
            }
            return new ApplicationDescriptor(name, asgs, statusCounts);
        }
    }
}
