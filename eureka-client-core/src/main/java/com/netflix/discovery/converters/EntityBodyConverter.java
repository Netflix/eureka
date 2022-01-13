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

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.netflix.discovery.provider.ISerializer;
import com.thoughtworks.xstream.XStream;

/**
 * A custom <tt>jersey</tt> provider implementation for eureka.
 *
 * <p>
 * The implementation uses <tt>Xstream</tt> to provide
 * serialization/deserialization capabilities. If the users to wish to provide
 * their own implementation they can do so by plugging in their own provider
 * here and annotating their classes with that provider by specifying the
 * {@link com.netflix.discovery.provider.Serializer} annotation.
 * <p>
 *
 * @author Karthik Ranganathan, Greg Kim.
 *
 */
public class EntityBodyConverter implements ISerializer {

    private static final String XML = "xml";
    private static final String JSON = "json";

    /*
     * (non-Javadoc)
     *
     * @see com.netflix.discovery.provider.ISerializer#read(java.io.InputStream,
     * java.lang.Class, javax.ws.rs.core.MediaType)
     */
    public Object read(InputStream is, Class type, MediaType mediaType)
            throws IOException {
        XStream xstream = getXStreamInstance(mediaType);
        if (xstream != null) {
            return xstream.fromXML(is);
        } else {
            throw new IllegalArgumentException("Content-type: "
                    + mediaType.getType() + " is currently not supported for "
                    + type.getName());
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.netflix.discovery.provider.ISerializer#write(java.lang.Object,
     * java.io.OutputStream, javax.ws.rs.core.MediaType)
     */
    public void write(Object object, OutputStream os, MediaType mediaType)
            throws IOException {
        XStream xstream = getXStreamInstance(mediaType);
        if (xstream != null) {
            xstream.toXML(object, os);
        } else {
            throw new IllegalArgumentException("Content-type: "
                    + mediaType.getType() + " is currently not supported for "
                    + object.getClass().getName());
        }
    }

    private XStream getXStreamInstance(MediaType mediaType) {
        XStream xstream = null;
        if (JSON.equalsIgnoreCase(mediaType.getSubtype())) {
            xstream = JsonXStream.getInstance();
        } else if (XML.equalsIgnoreCase(mediaType.getSubtype())) {
            xstream = XmlXStream.getInstance();
        }
        return xstream;
    }
}
