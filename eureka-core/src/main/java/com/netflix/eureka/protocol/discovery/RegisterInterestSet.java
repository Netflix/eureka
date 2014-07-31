package com.netflix.eureka.protocol.discovery;

import java.util.Arrays;

import com.netflix.eureka.registry.Interest;

/**
 * @author Tomasz Bak
 */
public class RegisterInterestSet {

    private final Interest[] interestSet;

    public RegisterInterestSet() {
        interestSet = null;
    }

    public RegisterInterestSet(Interest... interestSet) {
        this.interestSet = interestSet;
    }

    public Interest[] getInterestSet() {
        return interestSet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RegisterInterestSet that = (RegisterInterestSet) o;

        if (!Arrays.equals(interestSet, that.interestSet)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return interestSet != null ? Arrays.hashCode(interestSet) : 0;
    }

    @Override
    public String toString() {
        return "RegisterInterestSet{interestSet=" + interestSet + '}';
    }
}
