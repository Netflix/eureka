/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.rx.eureka.client.bootstrap;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.netflix.rx.eureka.client.ServerResolver;
import com.netflix.rx.eureka.client.ServerResolver.ServerEntry.Action;
import com.netflix.rx.eureka.utils.rx.ResourceObservable;
import com.netflix.rx.eureka.utils.rx.ResourceObservable.ResourceLoader;
import com.netflix.rx.eureka.utils.rx.ResourceObservable.ResourceLoaderException;
import com.netflix.rx.eureka.utils.rx.ResourceObservable.ResourceUpdate;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

/**
 * A list of server addresses read from a local configuration file. The file can be
 * optionally re-read at specified interval.
 * The file should consist of a set of lines, one per server in the following format:
 * <br>
 * {@code host name | ip address [;protocol=(TcpRegistration|TcpDiscovery)&port=port_number]}
 *
 * @author Tomasz Bak
 */
public class FileServerResolver implements ServerResolver<InetSocketAddress> {

    private final File textFile;
    private final boolean alwaysReload;
    private final Observable<ServerEntry<InetSocketAddress>> resolverObservable;

    public FileServerResolver(File textFile, long reloadInterval, TimeUnit timeUnit) {
        this(textFile, reloadInterval, -1, timeUnit, Schedulers.io());
    }

    public FileServerResolver(File textFile, long reloadInterval, long idleTimeout, TimeUnit reloadUnit, Scheduler scheduler) {
        this(textFile, reloadInterval, idleTimeout, reloadUnit, false, scheduler);
    }

    public FileServerResolver(File textFile, long reloadInterval, long idleTimeout, TimeUnit reloadUnit, boolean alwaysReload, Scheduler scheduler) {
        this.textFile = textFile;
        this.alwaysReload = alwaysReload;
        this.resolverObservable = ResourceObservable.fromResource(new FileResolveTask(), reloadInterval, idleTimeout, reloadUnit, scheduler);
    }

    @Override
    public Observable<ServerEntry<InetSocketAddress>> resolve() {
        return resolverObservable;
    }

    class FileResolveTask implements ResourceLoader<ServerEntry<InetSocketAddress>> {

        private long lastModified = -1;

        @Override
        public ResourceUpdate<ServerEntry<InetSocketAddress>> reload(Set<ServerEntry<InetSocketAddress>> currentSnapshot) {
            if (!isUpdated()) {
                return new ResourceUpdate<>(currentSnapshot, Collections.<ServerEntry<InetSocketAddress>>emptySet());
            }
            try {
                LineNumberReader reader = new LineNumberReader(new FileReader(textFile));
                try {
                    Set<ServerEntry<InetSocketAddress>> newAddresses = new HashSet<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!(line = line.trim()).isEmpty()) {
                            newAddresses.add(parseLine(reader.getLineNumber(), line));
                        }
                    }
                    return new ResourceUpdate<>(newAddresses, ServerEntry.cancellationSet(currentSnapshot, newAddresses));
                } finally {
                    reader.close();
                }
            } catch (IOException e) {
                if (lastModified == -1) {
                    throw new ResourceLoaderException("Server resolver file missing on startup " + textFile, false, e);
                }
                throw new ResourceLoaderException("Cannot reload server resolver file " + textFile, true, e);
            }
        }

        private ServerEntry<InetSocketAddress> parseLine(int lineNumber, String line) {
            int idx = line.indexOf(';');
            if (idx == -1) {
                return new ServerEntry<InetSocketAddress>(Action.Add, new InetSocketAddress(line, 0));
            }
            String address = line.substring(0, idx);
            ProtocolType protocolType = null;
            Integer port = null;
            int pos = idx + 1;
            while (pos < line.length()) {
                int eqIdx = line.indexOf('=', pos);
                if (eqIdx == -1) {
                    throw new IllegalArgumentException("Syntax error at line " + lineNumber);
                }
                String name = line.substring(pos, eqIdx);
                int ampIdx = line.indexOf('&', eqIdx + 1);
                if (ampIdx == -1) {
                    ampIdx = line.length();
                }
                String value = line.substring(eqIdx + 1, ampIdx);
                if ("protocol".equals(name)) {
                    try {
                        protocolType = ProtocolType.valueOf(value);
                    } catch (IllegalArgumentException ignored) {
                        throw new IllegalArgumentException("Syntax error at line " + lineNumber + " - unrecognized protocol");
                    }
                } else if ("port".equals(name)) {
                    try {
                        port = Integer.valueOf(value);
                    } catch (NumberFormatException ignored) {
                        throw new IllegalArgumentException("Syntax error at line " + lineNumber + " - not valid port number");
                    }
                } else {
                    throw new IllegalArgumentException("Syntax error at line " + lineNumber + " - unrecognized property");
                }
            }
            if (protocolType == null && port == null) {
                return new ServerEntry<InetSocketAddress>(Action.Add, new InetSocketAddress(line, 0));
            }
            if (protocolType == null || port == null) {
                throw new IllegalArgumentException("Syntax error at line " + lineNumber + " - both protocol and port number must be defined");
            }
            return new ServerEntry<InetSocketAddress>(Action.Add, new InetSocketAddress(address, port), new Protocol(port, protocolType));
        }

        boolean isUpdated() {
            if (alwaysReload) {
                return true;
            }
            long newLastModified = textFile.lastModified();
            if (newLastModified <= lastModified) {
                lastModified = newLastModified;
                return true;
            }
            return false;
        }
    }
}
