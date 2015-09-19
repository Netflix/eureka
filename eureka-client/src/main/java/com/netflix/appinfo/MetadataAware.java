package com.netflix.appinfo;

import java.util.Map;

public interface MetadataAware {
    Map<String, String> getMetadata();
    void setMetadata(Map<String, String> metadata);
}
