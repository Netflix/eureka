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

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.CharBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.discovery.util.DeserializerStringCache;
import com.netflix.discovery.util.DeserializerStringCache.CacheScope;
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
public class StringInterningAmazonInfoBuilder extends JsonDeserializer<AmazonInfo>{

    private static final Map<String, CacheScope> VALUE_INTERN_KEYS;
    private static final CharBuffer BUF_METADATA = CharBuffer.wrap("metadata");

    static {
        HashMap<String, CacheScope> keys = new HashMap<>();
        keys.put(MetaDataKey.accountId.getName(), CacheScope.GLOBAL_SCOPE);
        keys.put(MetaDataKey.amiId.getName(), CacheScope.GLOBAL_SCOPE);
        keys.put(MetaDataKey.availabilityZone.getName(), CacheScope.GLOBAL_SCOPE);
        keys.put(MetaDataKey.instanceType.getName(), CacheScope.GLOBAL_SCOPE);
        keys.put(MetaDataKey.vpcId.getName(), CacheScope.APPLICATION_SCOPE);
        VALUE_INTERN_KEYS = keys;
    }

    private HashMap<String, String> metadata;

    public StringInterningAmazonInfoBuilder withName(String name) {
        return this;
    }

    public StringInterningAmazonInfoBuilder withMetadata(HashMap<String, String> metadata) {
        this.metadata = metadata;
        if (metadata.isEmpty()) {
            return this;
        }
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String key = entry.getKey().intern();
            if (VALUE_INTERN_KEYS.containsKey(key)) {
                entry.setValue(StringCache.intern(entry.getValue()));
            }
        }
        return this;
    }

    public AmazonInfo build() {
        return new AmazonInfo(Name.Amazon.name(), metadata);
    }

    @Override
    public AmazonInfo deserialize(JsonParser jp, DeserializationContext context)
            throws IOException {
        HashMap<String,String> metadata = new HashMap<>();
        DeserializerStringCache intern = DeserializerStringCache.from(context);        
        
        JsonToken jsonToken;
        while((jsonToken = jp.nextToken()) != JsonToken.END_OBJECT){
          jsonToken = jp.nextToken();
          CharBuffer fieldName = CharBuffer.wrap(jp.getTextCharacters(), jp.getTextOffset(), jp.getTextLength());            
            
            if (BUF_METADATA.equals(fieldName)) {
                jsonToken = jp.nextToken();                
                while((jsonToken = jp.nextToken()) != JsonToken.END_OBJECT) {
                    String metadataKey = intern.apply(jp, CacheScope.GLOBAL_SCOPE);
                    jp.nextToken();
                    CacheScope scope = VALUE_INTERN_KEYS.get(metadataKey);
                    String metadataValue =  (scope != null) ? intern.apply(jp, scope) : jp.getValueAsString();                    
                    metadata.put(metadataKey, metadataValue);
                }
            }
            else {
                jsonToken = jp.nextToken();                
            }
        }
        return new AmazonInfo(Name.Amazon.name(), metadata);
    }
  
}
