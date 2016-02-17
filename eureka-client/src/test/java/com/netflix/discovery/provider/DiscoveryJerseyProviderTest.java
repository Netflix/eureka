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

package com.netflix.discovery.provider;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.wrappers.CodecWrappers;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 */
public class DiscoveryJerseyProviderTest {

    private static final InstanceInfo INSTANCE = InstanceInfoGenerator.takeOne();

    private final DiscoveryJerseyProvider jerseyProvider = new DiscoveryJerseyProvider(
            CodecWrappers.getEncoder(CodecWrappers.JacksonJson.class),
            CodecWrappers.getDecoder(CodecWrappers.JacksonJson.class)
    );

    @Test
    public void testJsonEncodingDecoding() throws Exception {
        testEncodingDecoding(MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    public void testXmlEncodingDecoding() throws Exception {
        testEncodingDecoding(MediaType.APPLICATION_XML_TYPE);
    }

    @Test
    public void testDecodingWithUtf8CharsetExplicitlySet() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("charset", "UTF-8");
        testEncodingDecoding(new MediaType("application", "json", params));
    }

    private void testEncodingDecoding(MediaType mediaType) throws IOException {
        // Write
        assertThat(jerseyProvider.isWriteable(InstanceInfo.class, InstanceInfo.class, null, mediaType), is(true));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        jerseyProvider.writeTo(INSTANCE, InstanceInfo.class, InstanceInfo.class, null, mediaType, null, out);

        // Read
        assertThat(jerseyProvider.isReadable(InstanceInfo.class, InstanceInfo.class, null, mediaType), is(true));

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        InstanceInfo decodedInstance = (InstanceInfo) jerseyProvider.readFrom(InstanceInfo.class, InstanceInfo.class, null, mediaType, null, in);

        assertThat(decodedInstance, is(equalTo(INSTANCE)));
    }

    @Test
    public void testNonUtf8CharsetIsNotAccepted() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("charset", "ISO-8859");
        MediaType mediaTypeWithNonSupportedCharset = new MediaType("application", "json", params);

        assertThat(jerseyProvider.isReadable(InstanceInfo.class, InstanceInfo.class, null, mediaTypeWithNonSupportedCharset), is(false));
    }
}