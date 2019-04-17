package com.netflix.discovery.shared.resolver;

import java.util.List;

public interface EndpointRandomizer {
    <T extends EurekaEndpoint> List<T> randomize(List<T> list);
}
