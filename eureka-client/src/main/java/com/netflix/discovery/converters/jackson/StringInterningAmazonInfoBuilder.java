package com.netflix.discovery.converters.jackson;

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
