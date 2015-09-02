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

package com.netflix.eureka2.server.http;

import javax.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

/**
 * Expose resources from a given jar file. Primary use case for this handler is providing static resources for
 * web clients.
 *
 * @author Tomasz Bak
 */
@Singleton
public class JarResourcesRequestHandler implements RequestHandler<ByteBuf, ByteBuf> {

    private static final Logger logger = LoggerFactory.getLogger(JarResourcesRequestHandler.class);

    private static final Pattern JAR_FILE_PATH_RE = Pattern.compile("file:(.+)!/.*");

    private static final String DEFAULT_CONTENT_TYPE = "text/plain";
    private static final Map<String, String> CONTENT_TYPE_BY_EXT = new HashMap<>();

    static {
        CONTENT_TYPE_BY_EXT.put("html", "text/html");
        CONTENT_TYPE_BY_EXT.put("js", "text/javascript");
        CONTENT_TYPE_BY_EXT.put("css", "text/css");
    }

    private final String contextPath;
    private final String defaultPath;

    private final URLClassLoader resourceClassLoader;
    private final Map<String, byte[]> fileBuffer = new ConcurrentHashMap<>();

    public JarResourcesRequestHandler(String contextPath, String jarNamePattern, String defaultPath) {
        this.defaultPath = defaultPath;
        this.contextPath = contextPath.endsWith("/") ? contextPath : contextPath + '/';
        File jarFile = findJar(jarNamePattern);
        if (jarFile == null) {
            throw new IllegalArgumentException("No jar file found matching the pattern " + jarNamePattern);
        }
        try {
            resourceClassLoader = new URLClassLoader(new URL[]{jarFile.toURI().toURL()});
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        if (request.getHttpMethod() != HttpMethod.GET) {
            response.setStatus(HttpResponseStatus.METHOD_NOT_ALLOWED);
            return Observable.empty();
        }
        String fileName = extractFileNameFromPath(request.getPath());
        if (fileName == null) {
            return handleNotFound(response);
        }
        byte[] fileBody = loadFileBody(fileName);
        if (fileBody == null) {
            return handleNotFound(response);
        }
        return writeResponse(fileName, fileBody, response);
    }

    private Observable<Void> handleNotFound(HttpServerResponse<ByteBuf> response) {
        if (defaultPath == null) {
            response.setStatus(HttpResponseStatus.NOT_FOUND);
        } else {
            response.getHeaders().add(Names.LOCATION, defaultPath);
            response.setStatus(HttpResponseStatus.MOVED_PERMANENTLY);
        }
        return Observable.empty();
    }

    private String extractFileNameFromPath(String path) {
        if (!path.startsWith(contextPath)) {
            return null;
        }
        return path.substring(contextPath.length());
    }

    private byte[] loadFileBody(String fileName) {
        byte[] body = fileBuffer.get(fileName);
        if (body != null) {
            return body;
        }
        InputStream is = resourceClassLoader.getResourceAsStream(fileName);
        if (is == null) {
            return null;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) > 0) {
                bos.write(buf, 0, len);
            }
        } catch (IOException e) {
            logger.warn("Failed to load resource " + fileName, e);
            return null;
        } finally {
            try {
                is.close();
            } catch (IOException ignored) {
            }
        }
        body = bos.toByteArray();
        fileBuffer.put(fileName, body);
        return body;
    }

    private static Observable<Void> writeResponse(String fileName, byte[] fileBody, HttpServerResponse<ByteBuf> response) {
        String ext = fileExtension(fileName);
        String contentType = null;
        if (ext != null) {
            contentType = CONTENT_TYPE_BY_EXT.get(ext);
        }
        if (contentType == null) {
            contentType = DEFAULT_CONTENT_TYPE;
        }
        response.getHeaders().add(Names.CONTENT_TYPE, contentType);

        response.setStatus(HttpResponseStatus.OK);
        return response.writeAndFlush(Unpooled.wrappedBuffer(fileBody));
    }

    private static String fileExtension(String fileName) {
        int dotPos = fileName.lastIndexOf('.');
        return dotPos == -1 ? null : fileName.substring(dotPos + 1);
    }

    /* Visibile for testing */
    static File findJar(String jarNamePattern) {
        Pattern pattern = Pattern.compile(jarNamePattern);
        Enumeration<URL> resources;
        try {
            // As there is no way to directly iterate over jars with base ClassLoader API, depend on manifest file
            // discovery, which should be present in each jar file.
            resources = JarResourcesRequestHandler.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot access resources on classpath", e);
        }
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            if ("jar".equals(url.getProtocol())) {
                Matcher matcher = JAR_FILE_PATH_RE.matcher(url.getFile());
                if (matcher.matches()) {
                    File file = new File(matcher.group(1));
                    if (pattern.matcher(file.getName()).matches()) {
                        return file;
                    }
                }
            }
        }
        return null;
    }
}
