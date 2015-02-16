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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.Server;
import com.netflix.eureka2.utils.rx.ResourceObservable;
import com.netflix.eureka2.utils.rx.ResourceObservable.ResourceLoader;
import com.netflix.eureka2.utils.rx.ResourceObservable.ResourceLoaderException;
import com.netflix.eureka2.utils.rx.ResourceObservable.ResourceUpdate;
import netflix.ocelli.LoadBalancerBuilder;
import netflix.ocelli.loadbalancer.DefaultLoadBalancerBuilder;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

/**
 * A list of server addresses read from a local configuration file. The file can be
 * optionally re-read at specified interval.
 * The file should consist of a set of lines, one per server in the following format:
 * <br>
 * {@code host name | ip address [;port=port_number]}
 *
 * @author Tomasz Bak
 */
public class FileServerResolver extends AbstractServerResolver {

    private final File textFile;
    private final TimeUnit timeUnit;
    private final boolean alwaysReload;
    private final Scheduler scheduler;
    private final long reloadInterval;
    private final long idleTimeout;

    public FileServerResolver(File textFile, long reloadInterval, long idleTimeout, TimeUnit timeUnit,
                              boolean alwaysReload, LoadBalancerBuilder<Server> loadBalancerBuilder,
                              Scheduler scheduler) {
        super(loadBalancerBuilder);
        this.textFile = textFile;
        this.reloadInterval = reloadInterval;
        this.idleTimeout = idleTimeout;
        this.timeUnit = timeUnit;
        this.alwaysReload = alwaysReload;
        this.scheduler = scheduler;
    }

    @Override
    protected Observable<ChangeNotification<Server>> serverUpdates() {
        return ResourceObservable.fromResource(new FileResolveTask(), reloadInterval, idleTimeout, timeUnit, scheduler);
    }

    public static class FileServerResolverBuilder {

        private File textFile;

        private long idleTimeoutMs = -1;

        private long reloadIntervalMs = -1;

        private boolean alwaysReload;

        private LoadBalancerBuilder<Server> loadBalancerBuilder;

        private Scheduler scheduler = Schedulers.io();

        public FileServerResolverBuilder withTextFile(File textFile) {
            this.textFile = textFile;
            return this;
        }

        public FileServerResolverBuilder withIdleTimeout(long timeout, TimeUnit timeUnit) {
            this.idleTimeoutMs = timeUnit.toMillis(timeout);
            return this;
        }

        public FileServerResolverBuilder withReloadInterval(long interval, TimeUnit timeUnit) {
            this.reloadIntervalMs = timeUnit.toMillis(interval);
            return this;
        }

        public FileServerResolverBuilder withAlwaysReload(boolean alwaysReload) {
            this.alwaysReload = alwaysReload;
            return this;
        }

        public FileServerResolverBuilder withLoadBalancerBuilder(LoadBalancerBuilder<Server> loadBalancerBuilder) {
            this.loadBalancerBuilder = loadBalancerBuilder;
            return this;
        }

        public FileServerResolverBuilder withScheduler(Scheduler scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        public FileServerResolver build() {
            if (loadBalancerBuilder == null) {
                loadBalancerBuilder = new DefaultLoadBalancerBuilder<>(null);
            }
            return new FileServerResolver(
                    textFile,
                    reloadIntervalMs,
                    idleTimeoutMs,
                    TimeUnit.MILLISECONDS,
                    alwaysReload,
                    loadBalancerBuilder,
                    scheduler
            );
        }
    }

    class FileResolveTask implements ResourceLoader<ChangeNotification<Server>> {

        private long lastModified = -1;

        @Override
        public ResourceUpdate<ChangeNotification<Server>> reload(Set<ChangeNotification<Server>> currentSnapshot) {
            if (!isUpdated()) {
                return new ResourceUpdate<>(currentSnapshot, Collections.<ChangeNotification<Server>>emptySet());
            }
            try {
                try (LineNumberReader reader = new LineNumberReader(new FileReader(textFile))) {
                    Set<ChangeNotification<Server>> newAddresses = new HashSet<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!(line = line.trim()).isEmpty()) {
                            Server server = parseLine(reader.getLineNumber(), line);
                            newAddresses.add(new ChangeNotification<Server>(Kind.Add, server));
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

        private Set<ChangeNotification<Server>> cancellationSet(Set<ChangeNotification<Server>> currentSnapshot, Set<ChangeNotification<Server>> newAddresses) {
            Set<ChangeNotification<Server>> cancelled = new HashSet<>();
            for (ChangeNotification<Server> entry : currentSnapshot) {
                if (!newAddresses.contains(entry)) {
                    cancelled.add(new ChangeNotification<Server>(Kind.Delete, entry.getData()));
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
                pos = ampIdx + 1;
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
