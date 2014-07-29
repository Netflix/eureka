package com.netflix.eureka.registry;

/**
 * An interest can be specified as some matching value on a given index
 * @author David Liu
 */
public interface Interest {
    public Index getIndex();
    public String getValue();
}
