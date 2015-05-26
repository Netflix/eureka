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

package com.netflix.discovery.provider;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A contract for dispatching to a custom serialization/de-serialization mechanism from jersey.
 *
 * @author Karthik Ranganathan
 *
 */
public interface ISerializer {

    Object read(InputStream is, Class type, MediaType mediaType) throws IOException;

    void write(Object object, OutputStream os, MediaType mediaType) throws IOException;
}
