package com.netflix.appinfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

/**
 * @author Tomasz Bak
 */
public class MyDataCenterInfo implements DataCenterInfo, MetadataAware, UniqueIdentifier {
    private Name name = Name.MyOwn;
    private String id;
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
    public void setMetadata(@Nonnull Map<String, String> metadata) {
        this.metadata = metadata;
    }

    @Nullable
    @Override
    public String getId() {
        return this.id != null ? this.id : this.metadata.get("instance-id");
    }

    public void setId(String id) {
        this.id = id;
    }
}
