/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.rx.eureka.registry;

import java.util.Set;

/**
 * A data model representing a single port on a server, together with its meta information.
 * To avoid ambiguity, each port/service endpoint should be given a unique name, by the service provider.
 * Client needs to be aware of those names to extract the desired information from the {@link InstanceInfo} object.
 * If a service is bind to a specific server IP address(s), the address labels must be provided as well.
 * Otherwise, if no address label is provided it is assumed that the service is bind to all server addresses.
 *
 * @author Tomasz Bak
 */
public class ServicePort {

    private final String name;

    private final Integer port;

    private final boolean secure;

    private final Set<String> addressLabels;

    // For reflection
    protected ServicePort() {
        name = null;
        port = 0;
        secure = false;
        addressLabels = null;
    }

    public ServicePort(Integer port, boolean secure) {
        this(null, port, secure, null);
    }

    public ServicePort(String name, Integer port, boolean secure) {
        this(name, port, secure, null);
    }

    public ServicePort(String name, Integer port, boolean secure, Set<String> addressLabels) {
        this.name = name;
        this.port = port;
        this.secure = secure;
        this.addressLabels = addressLabels;
    }

    public String getName() {
        return name;
    }

    public Integer getPort() {
        return port;
    }

    public boolean isSecure() {
        return secure;
    }

    public Set<String> getAddressLabels() {
        return addressLabels;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ServicePort that = (ServicePort) o;

        if (secure != that.secure) {
            return false;
        }
        if (addressLabels != null ? !addressLabels.equals(that.addressLabels) : that.addressLabels != null) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (port != null ? !port.equals(that.port) : that.port != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (port != null ? port.hashCode() : 0);
        result = 31 * result + (secure ? 1 : 0);
        result = 31 * result + (addressLabels != null ? addressLabels.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ServicePort{" +
                "name='" + name + '\'' +
                ", port=" + port +
                ", secure=" + secure +
                ", addressLabels=" + addressLabels +
                '}';
    }

    public static class ServicePortBuilder {
        private String name;
        private Integer port;
        private boolean secure;
        private Set<String> addressLabels;

        private ServicePortBuilder() {
        }

        public static ServicePortBuilder aServicePort() {
            return new ServicePortBuilder();
        }

        public ServicePortBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public ServicePortBuilder withPort(Integer port) {
            this.port = port;
            return this;
        }

        public ServicePortBuilder withSecure(boolean secure) {
            this.secure = secure;
            return this;
        }

        public ServicePortBuilder withAddressLabels(Set<String> addressLabels) {
            this.addressLabels = addressLabels;
            return this;
        }

        public ServicePortBuilder but() {
            return aServicePort().withName(name).withPort(port).withSecure(secure).withAddressLabels(addressLabels);
        }

        public ServicePort build() {
            return new ServicePort(name, port, secure, addressLabels);
        }
    }
}
