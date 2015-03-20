package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.utils.rx.ResourceObservable;
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
public class FileServerResolver extends OcelliServerResolver {

    private final File textFile;
    private final Configuration configuration;

    FileServerResolver(File textFile) {
        this(textFile, new Configuration());
    }

    FileServerResolver(File textFile, Configuration configuration) {
        super(createServerSource(textFile, configuration));
        this.textFile = textFile;
        this.configuration = configuration;
    }

    public FileServerResolver configureReload(boolean alwaysReload, int reloadInterval, int idleTimeout, TimeUnit timeUnit) {
        Configuration updatedConfig = configuration
                .copy()
                .withAlwaysReload(alwaysReload)
                .withReloadInterval(reloadInterval)
                .withIdleTimeout(idleTimeout)
                .withTimeUnit(timeUnit);

        return new FileServerResolver(textFile, updatedConfig);
    }

    public FileServerResolver configureReloadScheduler(Scheduler scheduler) {
        Configuration updatedConfig = configuration
                .copy()
                .withScheduler(scheduler);

        return new FileServerResolver(textFile, updatedConfig);
    }

    private static Observable<ChangeNotification<Server>> createServerSource(File textFile, Configuration configuration) {
        return ResourceObservable.fromResource(
                new FileResolveTask(textFile, configuration),
                configuration.reloadInterval,
                configuration.idleTimeout,
                configuration.timeUnit,
                configuration.scheduler
        );
    }


    protected static class Configuration {
        // mutable fields set to defaults
        boolean alwaysReload = false;
        long reloadInterval = -1;
        long idleTimeout = -1;
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;
        Scheduler scheduler = Schedulers.io();

        public Configuration copy() {
            return new Configuration()
                    .withAlwaysReload(this.alwaysReload)
                    .withReloadInterval(this.reloadInterval)
                    .withIdleTimeout(this.idleTimeout)
                    .withTimeUnit(this.timeUnit)
                    .withScheduler(this.scheduler);
        }

        public Configuration withAlwaysReload(boolean alwaysReload) {
            this.alwaysReload = alwaysReload;
            return this;
        }

        public Configuration withReloadInterval(long reloadInterval) {
            this.reloadInterval = reloadInterval;
            return this;
        }

        public Configuration withIdleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        public Configuration withTimeUnit(TimeUnit reloadUnit) {
            this.timeUnit = reloadUnit;
            return this;
        }

        public Configuration withScheduler(Scheduler scheduler) {
            this.scheduler = scheduler;
            return this;
        }
    }


    private static class FileResolveTask implements ResourceObservable.ResourceLoader<ChangeNotification<Server>> {

        private final File textFile;
        private final Configuration configuration;

        private final ChangeNotification<Server> sentinel = ChangeNotification.bufferSentinel();
        private long lastModified = -1;

        FileResolveTask(File textFile, Configuration configuration) {
            this.textFile = textFile;
            this.configuration = configuration;
        }

        @Override
        public ResourceObservable.ResourceUpdate<ChangeNotification<Server>> reload(Set<ChangeNotification<Server>> currentSnapshot) {
            if (!isUpdated()) {
                return new ResourceObservable.ResourceUpdate<>(currentSnapshot, Collections.<ChangeNotification<Server>>emptySet(), sentinel);
            }
            try {
                try (LineNumberReader reader = new LineNumberReader(new FileReader(textFile))) {
                    Set<ChangeNotification<Server>> newAddresses = new HashSet<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!(line = line.trim()).isEmpty()) {
                            Server server = parseLine(reader.getLineNumber(), line);
                            newAddresses.add(new ChangeNotification<Server>(ChangeNotification.Kind.Add, server));
                        }
                    }
                    return new ResourceObservable.ResourceUpdate<>(newAddresses, cancellationSet(currentSnapshot, newAddresses), sentinel);
                }
            } catch (IOException e) {
                if (lastModified == -1) {
                    throw new ResourceObservable.ResourceLoaderException("Server resolver file missing on startup " + textFile, false, e);
                }
                throw new ResourceObservable.ResourceLoaderException("Cannot reload server resolver file " + textFile, true, e);
            }
        }

        private Set<ChangeNotification<Server>> cancellationSet(Set<ChangeNotification<Server>> currentSnapshot, Set<ChangeNotification<Server>> newAddresses) {
            Set<ChangeNotification<Server>> cancelled = new HashSet<>();
            for (ChangeNotification<Server> entry : currentSnapshot) {
                if (!newAddresses.contains(entry)) {
                    cancelled.add(new ChangeNotification<Server>(ChangeNotification.Kind.Delete, entry.getData()));
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
            if (configuration.alwaysReload) {
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
