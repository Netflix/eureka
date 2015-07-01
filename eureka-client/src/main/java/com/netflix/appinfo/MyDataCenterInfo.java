package com.netflix.appinfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Tomasz Bak
 */
public class MyDataCenterInfo implements DataCenterInfo {

    private final Name name;

    @JsonCreator
    public MyDataCenterInfo(@JsonProperty("name") Name name) {
        this.name = name;
    }

    @Override
    public Name getName() {
        return name;
    }
}
