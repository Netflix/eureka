/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.discovery.converters.jackson.builder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.discovery.util.StringCache;

/**
 * Amazon instance info builder that is doing key names interning, together with
 * value interning for selected keys (see {@link StringInterningAmazonInfoBuilder#VALUE_INTERN_KEYS}).
 *
 * The amount of string objects that is interned here is very limited in scope, and is done by calling
 * {@link String#intern()}, with no custom build string cache.
 *
 * @author Tomasz Bak
 */
public class StringInterningAmazonInfoBuilder {

    private static final Set<String> VALUE_INTERN_KEYS;

    static {
        HashSet<String> keys = new HashSet<>();
        keys.add(MetaDataKey.accountId.getName());
        keys.add(MetaDataKey.amiId.getName());
        keys.add(MetaDataKey.availabilityZone.getName());
        keys.add(MetaDataKey.instanceType.getName());
        keys.add(MetaDataKey.vpcId.getName());
        VALUE_INTERN_KEYS = keys;
    }

    private HashMap<String, String> metadata;

    public StringInterningAmazonInfoBuilder withName(String name) {
        return this;
    }

    public StringInterningAmazonInfoBuilder withMetadata(HashMap<String, String> metadata) {
        if (metadata.isEmpty()) {
            this.metadata = metadata;
            return this;
        }
        this.metadata = new HashMap<>();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String key = entry.getKey().intern();
            String value = entry.getValue();
            if (VALUE_INTERN_KEYS.contains(key)) {
                value = StringCache.intern(value);
            }
            this.metadata.put(key, value);
        }
        return this;
    }

    public AmazonInfo build() {
        return new AmazonInfo(Name.Amazon.name(), metadata);
    }
}
