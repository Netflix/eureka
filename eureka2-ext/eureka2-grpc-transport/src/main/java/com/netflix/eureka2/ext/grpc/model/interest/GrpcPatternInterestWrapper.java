/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.eureka2.ext.grpc.model.interest;

import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.model.instance.InstanceInfo;

import java.util.regex.Pattern;

/**
 */
public abstract class GrpcPatternInterestWrapper extends GrpcInterestWrapper {

    private final Eureka2.GrpcInterest grpcInterest;

    private volatile Pattern compiledPattern;

    protected GrpcPatternInterestWrapper(Eureka2.GrpcInterest grpcInterest) {
        this.grpcInterest = grpcInterest;
    }

    @Override
    public boolean matches(InstanceInfo data) {
        String value = getValue(data);
        if (value == null) {
            return false;
        }
        if (getOperator() != Operator.Like) {
            return getPattern().equals(value);
        }
        if (compiledPattern == null) {
            compiledPattern = Pattern.compile(getPattern());
        }
        return compiledPattern.matcher(value).matches();
    }

    protected abstract String getValue(InstanceInfo data);

    @Override
    public Eureka2.GrpcInterest getGrpcObject() {
        return grpcInterest;
    }

    @Override
    public boolean isAtomicInterest() {
        return true;
    }

    // compiledPattern is NOT part of equals
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        GrpcPatternInterestWrapper that = (GrpcPatternInterestWrapper) o;

        if (getOperator() != that.getOperator())
            return false;
        if (getPattern() != null ? !getPattern().equals(that.getPattern()) : that.getPattern() != null)
            return false;

        return true;
    }

    // compiledPattern is NOT part of hashCode
    @Override
    public int hashCode() {
        int result = getPattern() != null ? getPattern().hashCode() : 0;
        result = 31 * result + (getOperator() != null ? getOperator().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '{' +
                "pattern='" + getPattern() + '\'' +
                ", operator=" + getOperator() +
                ", compiledPattern=" + compiledPattern +
                '}';
    }
}
