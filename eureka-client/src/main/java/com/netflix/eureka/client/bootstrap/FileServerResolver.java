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

package com.netflix.eureka.client.bootstrap;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka.client.ServerResolver.ServerEntry.Action;
import rx.Scheduler;
import rx.schedulers.Schedulers;

/**
 * A list of server addresses read from a local configuration file. The file can be
 * optionally re-read at specified interval.
 * The file should consist of a set of lines, one per server in the following format:
 * <br>
 * {@code host name | ip address [;protocol=(json|avro)&port=port_number]}
 *
 * @author Tomasz Bak
 */
public class FileServerResolver extends AbstractServerResolver<InetSocketAddress> {

    private static final long FILE_RELOAD_INTERVAL = 30;
    private static final TimeUnit FILE_RELOAD_INTERVAL_UNIT = TimeUnit.SECONDS;

    private final File textFile;
    private final long reloadInterval;
    private final TimeUnit reloadUnit;

    public FileServerResolver(File textFile, boolean refresh) {
        this(textFile, refresh, FILE_RELOAD_INTERVAL, FILE_RELOAD_INTERVAL_UNIT, Schedulers.io());
    }

    public FileServerResolver(File textFile, boolean refresh, long reloadInterval, TimeUnit reloadUnit, Scheduler scheduler) {
        super(refresh, scheduler);
        this.textFile = textFile;
        this.reloadInterval = reloadInterval;
        this.reloadUnit = reloadUnit;
    }

    @Override
    protected ResolverTask createResolveTask() {
        return new FileResolveTask();
    }

    @Override
    protected IOException noServersFound(Exception ex) {
        if (ex == null) {
            return new IOException("File " + textFile + " does not contain any server");
        }
        return new IOException("Error during reading file " + textFile, ex);
    }

    class FileResolveTask implements ResolverTask {

        private long lastModified = -1;

        @Override
        public void call() {
            boolean reschedule;
            if (!isUpdated()) {
                reschedule = true;
            } else {
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
                        reschedule = onServerListUpdate(newAddresses);
                    } finally {
                        reader.close();
                    }
                } catch (IOException e) {
                    reschedule = onUpdateError(e);
                }
            }
            if (reschedule) {
                scheduleLookup(this, reloadInterval, reloadUnit);
            }
        }

        private ServerEntry<InetSocketAddress> parseLine(int lineNumber, String line) {
            int idx = line.indexOf(';');
            if (idx == -1) {
                return new ServerEntry<InetSocketAddress>(Action.Add, new InetSocketAddress(line, 0));
            }
            String address = line.substring(0, idx);
            Protocol protocol = null;
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
                        protocol = Protocol.valueOf(value);
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
            if (protocol == null && port == null) {
                return new ServerEntry<InetSocketAddress>(Action.Add, new InetSocketAddress(line, 0));
            }
            if (protocol == null || port == null) {
                throw new IllegalArgumentException("Syntax error at line " + lineNumber + " - both protocol and port number must be defined");
            }
            return new ServerEntry<InetSocketAddress>(Action.Add, new InetSocketAddress(address, port), protocol);
        }

        boolean isUpdated() {
            long newLastModified = textFile.lastModified();
            if (newLastModified <= lastModified) {
                lastModified = newLastModified;
                return true;
            }
            return false;
        }
    }
}
