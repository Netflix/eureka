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

package com.netflix.discovery.converters.jackson;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.KeyFormatter;
import com.netflix.discovery.converters.jackson.mixin.ApplicationXmlMixIn;
import com.netflix.discovery.converters.jackson.mixin.ApplicationsXmlMixIn;
import com.netflix.discovery.converters.jackson.mixin.DataCenterInfoXmlMixIn;
import com.netflix.discovery.converters.jackson.mixin.PortWrapperXmlMixIn;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;

/**
 * @author Tomasz Bak
 */
public class EurekaXmlJacksonCodec extends AbstractEurekaJacksonCodec {

    private final XmlMapper xmlMapper;

    public EurekaXmlJacksonCodec() {
        this(KeyFormatter.defaultKeyFormatter(), false);
    }

    public EurekaXmlJacksonCodec(final KeyFormatter keyFormatter, boolean compact) {
        xmlMapper = new XmlMapper() {
            public ObjectMapper registerModule(Module module) {
                setSerializerFactory(
                        getSerializerFactory().withSerializerModifier(EurekaJacksonXmlModifiers.createXmlSerializerModifier(keyFormatter))
                );
                return super.registerModule(module);
            }
        };
        xmlMapper.setSerializationInclusion(Include.NON_NULL);
        xmlMapper.addMixIn(DataCenterInfo.class, DataCenterInfoXmlMixIn.class);
        xmlMapper.addMixIn(InstanceInfo.PortWrapper.class, PortWrapperXmlMixIn.class);
        xmlMapper.addMixIn(Application.class, ApplicationXmlMixIn.class);
        xmlMapper.addMixIn(Applications.class, ApplicationsXmlMixIn.class);
        SimpleModule xmlModule = new SimpleModule();
        xmlMapper.registerModule(xmlModule);

        if (compact) {
            addMiniConfig(xmlMapper);
        }
    }

    @Override
    public <T> ObjectMapper getObjectMapper(Class<T> type) {
        return xmlMapper;
    }
}
