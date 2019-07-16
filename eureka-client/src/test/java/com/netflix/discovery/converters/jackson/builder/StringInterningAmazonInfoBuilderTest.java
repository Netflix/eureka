/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.discovery.converters.jackson.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.netflix.appinfo.AmazonInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;

public class StringInterningAmazonInfoBuilderTest {

    private ObjectMapper newMapper() {
        SimpleModule module = new SimpleModule()
                .addDeserializer(AmazonInfo.class, new StringInterningAmazonInfoBuilder());
        return new ObjectMapper().registerModule(module);
    }

    /**
     * Convert to AmazonInfo with a simple map instead of a compact map. The compact map
     * doesn't have a custom toString and makes it hard to understand diffs in assertions.
     */
    private AmazonInfo nonCompact(AmazonInfo info) {
        return new AmazonInfo(info.getName().name(), new HashMap<>(info.getMetadata()));
    }

    @Test(expected = InvalidTypeIdException.class)
    public void payloadThatIsEmpty() throws IOException {
        newMapper().readValue("{}", AmazonInfo.class);
    }

    @Test
    public void payloadWithJustClass() throws IOException {
        String json = "{"
                + "\"@class\": \"com.netflix.appinfo.AmazonInfo\""
                + "}";
        AmazonInfo info = newMapper().readValue(json, AmazonInfo.class);
        Assert.assertEquals(new AmazonInfo(), info);
    }

    @Test
    public void payloadWithClassAndMetadata() throws IOException {
        String json = "{"
                + "    \"@class\": \"com.netflix.appinfo.AmazonInfo\","
                + "    \"metadata\": {"
                + "        \"instance-id\": \"i-12345\""
                + "    }"
                + "}";
        AmazonInfo info = newMapper().readValue(json, AmazonInfo.class);

        AmazonInfo expected = AmazonInfo.Builder.newBuilder()
                .addMetadata(AmazonInfo.MetaDataKey.instanceId, "i-12345")
                .build();
        Assert.assertEquals(expected, nonCompact(info));
    }

    @Test
    public void payloadWithClassAfterMetadata() throws IOException {
        String json = "{"
                + "    \"metadata\": {"
                + "        \"instance-id\": \"i-12345\""
                + "    },"
                + "    \"@class\": \"com.netflix.appinfo.AmazonInfo\""
                + "}";
        AmazonInfo info = newMapper().readValue(json, AmazonInfo.class);

        AmazonInfo expected = AmazonInfo.Builder.newBuilder()
                .addMetadata(AmazonInfo.MetaDataKey.instanceId, "i-12345")
                .build();
        Assert.assertEquals(expected, nonCompact(info));
    }

    @Test
    public void payloadWithNameBeforeMetadata() throws IOException {
        String json = "{"
                + "    \"@class\": \"com.netflix.appinfo.AmazonInfo\","
                + "    \"name\": \"Amazon\","
                + "    \"metadata\": {"
                + "        \"instance-id\": \"i-12345\""
                + "    }"
                + "}";
        AmazonInfo info = newMapper().readValue(json, AmazonInfo.class);

        AmazonInfo expected = AmazonInfo.Builder.newBuilder()
                .addMetadata(AmazonInfo.MetaDataKey.instanceId, "i-12345")
                .build();
        Assert.assertEquals(expected, nonCompact(info));
    }

    @Test
    public void payloadWithNameAfterMetadata() throws IOException {
        String json = "{"
                + "    \"@class\": \"com.netflix.appinfo.AmazonInfo\","
                + "    \"metadata\": {"
                + "        \"instance-id\": \"i-12345\""
                + "    },"
                + "    \"name\": \"Amazon\""
                + "}";
        AmazonInfo info = newMapper().readValue(json, AmazonInfo.class);

        AmazonInfo expected = AmazonInfo.Builder.newBuilder()
                .addMetadata(AmazonInfo.MetaDataKey.instanceId, "i-12345")
                .build();
        Assert.assertEquals(expected, nonCompact(info));
    }

    @Test
    public void payloadWithOtherStuffBeforeAndAfterMetadata() throws IOException {
        String json = "{"
                + "    \"@class\": \"com.netflix.appinfo.AmazonInfo\","
                + "    \"foo\": \"bar\","
                + "    \"metadata\": {"
                + "        \"instance-id\": \"i-12345\""
                + "    },"
                + "    \"bar\": \"baz\","
                + "    \"name\": \"Amazon\""
                + "}";
        AmazonInfo info = newMapper().readValue(json, AmazonInfo.class);

        AmazonInfo expected = AmazonInfo.Builder.newBuilder()
                .addMetadata(AmazonInfo.MetaDataKey.instanceId, "i-12345")
                .build();
        Assert.assertEquals(expected, nonCompact(info));
    }
}
