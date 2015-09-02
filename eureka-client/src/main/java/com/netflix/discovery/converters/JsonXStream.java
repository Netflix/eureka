/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.discovery.converters;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.naming.NameCoder;
import com.thoughtworks.xstream.io.xml.XmlFriendlyNameCoder;

/**
 * An <tt>Xstream</tt> specific implementation for serializing and deserializing
 * to/from JSON format.
 *
 * <p>
 * This class also allows configuration of custom serializers with Xstream.
 * </p>
 *
 * @author Karthik Ranganathan
 *
 */
public class JsonXStream extends XStream {

    private static final JsonXStream s_instance = new JsonXStream();

    public JsonXStream() {
        super(new JettisonMappedXmlDriver() {
            private final NameCoder coder = initializeNameCoder();

            protected NameCoder getNameCoder() {
                return this.coder;
            }
        });

        registerConverter(new Converters.ApplicationConverter());
        registerConverter(new Converters.ApplicationsConverter());
        registerConverter(new Converters.DataCenterInfoConverter());
        registerConverter(new Converters.InstanceInfoConverter());
        registerConverter(new Converters.LeaseInfoConverter());
        registerConverter(new Converters.MetadataConverter());
        setMode(XStream.NO_REFERENCES);
        processAnnotations(new Class[]{InstanceInfo.class, Application.class, Applications.class});
    }

    public static JsonXStream getInstance() {
        return s_instance;
    }

    private static XmlFriendlyNameCoder initializeNameCoder() {
        EurekaClientConfig clientConfig = DiscoveryManager
                .getInstance().getEurekaClientConfig();
        if (clientConfig == null) {
            return new XmlFriendlyNameCoder();
        }
        return new XmlFriendlyNameCoder(clientConfig.getDollarReplacement(), clientConfig.getEscapeCharReplacement());
    }

}
