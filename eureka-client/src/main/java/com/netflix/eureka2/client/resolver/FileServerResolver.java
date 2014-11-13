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

package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.utils.rx.ResourceObservable;
import com.netflix.eureka2.utils.rx.ResourceObservable.ResourceLoader;
import com.netflix.eureka2.utils.rx.ResourceObservable.ResourceLoaderException;
import com.netflix.eureka2.utils.rx.ResourceObservable.ResourceUpdate;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A list of server addresses read from a local configuration file. The file can be
 * optionally re-read at specified interval.
 * The file should consist of a set of lines, one per server in the following format:
 * <br>
 * {@code host name | ip address [;port=port_number]}
 *
 * @author Tomasz Bak
 */
public class FileServerResolver implements ServerResolver {

    private final File textFile;
    private final boolean alwaysReload;
    private final Observable<Server> resolverObservable;

    public FileServerResolver(File textFile) {
        this(textFile, -1, TimeUnit.MINUTES);
    }

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
    public Observable<Server> resolve() {
        return resolverObservable;
    }

    class FileResolveTask implements ResourceLoader<Server> {

        private long lastModified = -1;

        @Override
        public ResourceUpdate<Server> reload(Set<Server> currentSnapshot) {
            if (!isUpdated()) {
                return new ResourceUpdate<>(currentSnapshot, Collections.<Server>emptySet());
            }
            try {
                try (LineNumberReader reader = new LineNumberReader(new FileReader(textFile))) {
                    Set<Server> newAddresses = new HashSet<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!(line = line.trim()).isEmpty()) {
                            newAddresses.add(parseLine(reader.getLineNumber(), line));
                        }
                    }
                    return new ResourceUpdate<>(newAddresses, cancellationSet(currentSnapshot, newAddresses));
                }
            } catch (IOException e) {
                if (lastModified == -1) {
                    throw new ResourceLoaderException("Server resolver file missing on startup " + textFile, false, e);
                }
                throw new ResourceLoaderException("Cannot reload server resolver file " + textFile, true, e);
            }
        }

        private Set<Server> cancellationSet(Set<Server> currentSnapshot, Set<Server> newAddresses) {
            Set<Server> cancelled = new HashSet<>();
            for (Server entry : currentSnapshot) {
                if (!newAddresses.contains(entry)) {
                    cancelled.add(entry);
                }
            }
            return cancelled;
        }

        private Server parseLine(int lineNumber, String line) {
            int idx = line.indexOf(';');
            if (idx == -1) {
                return new Server(line, 0);
            }
            String address = line.substring(0, idx);
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
                if ("port".equals(name)) {
                    try {
                        port = Integer.valueOf(value);
                    } catch (NumberFormatException ignored) {
                        throw new IllegalArgumentException("Syntax error at line " + lineNumber + " - not valid port number");
                    }
                } else {
                    throw new IllegalArgumentException("Syntax error at line " + lineNumber + " - unrecognized property");
                }
            }
            if (port == null) {
                throw new IllegalArgumentException("Syntax error at line " + lineNumber + " - port number must be defined");
            }
            return new Server(address, port);
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
