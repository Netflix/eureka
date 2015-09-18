package com.netflix.appinfo;

import java.util.Collections;
import java.util.Map;

/**
 * @author Tomasz Bak
 */
public class MyDataCenterInfo implements DataCenterInfo, MetadataAware {
    private Name name = Name.MyOwn;
    private Map<String, String> metadata = Collections.emptyMap();

    public MyDataCenterInfo() {}

    public MyDataCenterInfo(Name name) {
        this.name = name;
    }

    @Override
    public Name getName() {
        return name;
    }

    public void setName(Name name) {
        this.name = name;
    }

    @Override
    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
