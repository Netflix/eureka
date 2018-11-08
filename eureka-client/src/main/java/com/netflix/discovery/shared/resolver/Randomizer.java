package com.netflix.discovery.shared.resolver;

import java.util.List;

public interface Randomizer<T> {
    void randomize(List<T> urlList);
}
