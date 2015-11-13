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

package com.netflix.eureka2.model.interest;

import java.util.regex.Pattern;

/**
 * @author Tomasz Bak
 */
public abstract class StdAbstractPatternInterest<T> implements Interest<T> {

    private final String pattern;
    private final Operator operator;

    private volatile Pattern compiledPattern;

    /* For serializer */
    protected StdAbstractPatternInterest() {
        pattern = null;
        operator = null;
    }

    protected StdAbstractPatternInterest(String pattern) {
        this(pattern, Operator.Equals);
    }

    protected StdAbstractPatternInterest(String pattern, Operator operator) {
        if (pattern == null) {
            throw new IllegalArgumentException("Expected non null pattern value");
        }
        if (operator == null) {
            throw new IllegalArgumentException("Expected non null operator value");
        }
        this.pattern = pattern;
        this.operator = operator;
    }

    public String getPattern() {
        return pattern;
    }

    @Override
    public Operator getOperator() {
        return operator;
    }

    /*visible for testing*/ Pattern getCompiledPattern() {
        return compiledPattern;
    }

    @Override
    public boolean matches(T data) {
        String value = getValue(data);
        if (value == null) {
            return false;
        }
        if (operator != Operator.Like) {
            return pattern.equals(value);
        }
        if (compiledPattern == null) {
            compiledPattern = Pattern.compile(pattern);
        }
        return compiledPattern.matcher(value).matches();
    }

    protected abstract String getValue(T data);

    // compiledPattern is NOT part of equals
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        StdAbstractPatternInterest that = (StdAbstractPatternInterest) o;

        if (operator != that.operator)
            return false;
        if (pattern != null ? !pattern.equals(that.pattern) : that.pattern != null)
            return false;

        return true;
    }

    // compiledPattern is NOT part of hashCode
    @Override
    public int hashCode() {
        int result = pattern != null ? pattern.hashCode() : 0;
        result = 31 * result + (operator != null ? operator.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '{' +
                "pattern='" + pattern + '\'' +
                ", operator=" + operator +
                ", compiledPattern=" + compiledPattern +
                '}';
    }
}
