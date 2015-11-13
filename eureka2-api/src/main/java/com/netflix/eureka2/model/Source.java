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

package com.netflix.eureka2.model;

/**
 */
public interface Source {

    /**
     * Each entry in a registry is associated with exactly one origin:
     * <ul>
     * <li>{@link #LOCAL}</li> - there is an opened registration client connection to the write local server
     * <li>{@link #REPLICATED}</li> - replicated entry from another server
     * <li>{@link #BOOTSTRAP}</li> - entry loaded from external, bootstrap resource
     * <li>{@link #INTERESTED}</li> - entry from a source server specified as an interest
     * </ul>
     */
    enum Origin {
        LOCAL, REPLICATED, BOOTSTRAP, INTERESTED
    }

    Origin getOrigin();

    String getName();

    long getId();

    String getOriginNamePair();

    /**
     * @return a matcher that matches the given source exactly (match on origin:name:id). This is the same as equals
     */
    static SourceMatcher matcherFor(final Source source) {
        return new SourceMatcher() {
            @Override
            public boolean match(Source another) {
                return (source == null)
                        ? (another == null)
                        : source.equals(another);
            }

            @Override
            public String toString() {
                return "SourceMatcher{" + source.toString() + "}";
            }
        };
    }

    /**
     * @return a matcher that matches the given origin
     */
    static SourceMatcher matcherFor(final Origin origin) {
        return new SourceMatcher() {
            @Override
            public boolean match(Source another) {
                if (another == null) {
                    return false;
                }
                return origin.equals(another.getOrigin());
            }

            @Override
            public String toString() {
                return "OriginMatcher{" + origin.toString() + "}";
            }
        };
    }

    /**
     * @return a matcher that matches the given source on origin:name only, but not id
     */
    static SourceMatcher matcherFor(final Origin origin, final String name) {
        return new SourceMatcher() {
            @Override
            public boolean match(Source another) {
                if (another == null) {
                    return false;
                }
                boolean originMatches = origin.equals(another.getOrigin());
                boolean nameMatches = (name == null)
                        ? (another.getName() == null)
                        : name.equals(another.getName());
                return originMatches && nameMatches;
            }
        };
    }

    abstract class SourceMatcher {
        public abstract boolean match(Source another);
    }
}
