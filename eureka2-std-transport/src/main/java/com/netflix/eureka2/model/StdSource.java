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

import java.util.UUID;

/**
 * A source class that must contain the origin of the source, and optionally a name. An unique id will be generated
 * for each source regardless of it's origin or name.
 *
 * @author David Liu
 */
public class StdSource implements Source {

    private final Origin origin;
    private final String name;  // nullable
    private final long id;

    private StdSource() {  // for Json and Avro
        this(null);
    }

    public StdSource(Origin origin) {
        this(origin, null);
    }

    public StdSource(Origin origin, String name) {
        this(origin, name, UUID.randomUUID().getLeastSignificantBits());
    }

    public StdSource(Origin origin, String name, long id) {
        this.origin = origin;
        this.name = name;
        this.id = id;
    }

    public Origin getOrigin() {
        return origin;
    }

    public String getName() {
        return name;
    }

    public long getId() {
        return id;
    }

    public String getOriginNamePair() {
        return origin.name() + (name == null ? "" : name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StdSource)) return false;

        StdSource source = (StdSource) o;

        if (id != source.id) return false;
        if (name != null ? !name.equals(source.name) : source.name != null) return false;
        if (origin != source.origin) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = origin != null ? origin.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (int) (id ^ (id >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "Source{" +
                "origin=" + origin +
                ", name='" + name + '\'' +
                ", id='" + id + '\'' +
                '}';
    }

}
