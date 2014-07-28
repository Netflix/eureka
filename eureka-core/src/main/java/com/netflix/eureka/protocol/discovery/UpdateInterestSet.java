package com.netflix.eureka.protocol.discovery;

/**
 * @author Tomasz Bak
 */
public class UpdateInterestSet {

    // FIXME: use interest set data model once it is created
    private final Object interestSet;

    public UpdateInterestSet(Object interestSet) {
        this.interestSet = interestSet;
    }

    public Object getInterestSet() {
        return interestSet;
    }
}
