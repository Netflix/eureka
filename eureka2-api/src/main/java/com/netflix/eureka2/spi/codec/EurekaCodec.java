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

package com.netflix.eureka2.spi.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Codec API. By providing this common abstraction, the same codecs can be used by
 * transport, and non-transport related modules (for example registry external backup).
 *
 * @author Tomasz Bak
 */
public abstract class EurekaCodec {

    private static volatile EurekaCodec defaultCodec;

    public abstract boolean accept(Class<?> valueType);

    public abstract <T> void encode(T value, OutputStream output) throws IOException;

    public abstract <T> T decode(InputStream source, Class<T> entityType) throws IOException;

    public static EurekaCodec getDefaultCodec() {
        return defaultCodec;
    }

    public static EurekaCodec setDefaultCodec(EurekaCodec newCodec) {
        EurekaCodec previous = defaultCodec;
        defaultCodec = newCodec;
        return previous;
    }

}
