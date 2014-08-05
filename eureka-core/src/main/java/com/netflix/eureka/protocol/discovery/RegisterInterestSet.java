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

package com.netflix.eureka.protocol.discovery;

import com.netflix.eureka.interests.Interest;

import java.util.Arrays;
import java.util.List;

/**
 * @author Tomasz Bak
 */
public class RegisterInterestSet {

    private final Interest[] interestSet;

    public RegisterInterestSet() {
        interestSet = null;
    }

    public RegisterInterestSet(List<Interest> interestSet) {
        this.interestSet = new Interest[interestSet.size()];
        interestSet.toArray(this.interestSet);
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
