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

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Load server list from DNS. Optionally, the list can be refreshed at a specified
 * interval.
 *
 * @author Tomasz Bak
 */
public class DnsServerResolver implements ServerResolver {

    private static final long DNS_LOOKUP_INTERVAL = 30;
    private static final long IDLE_TIMEOUT = 300;

    private final String domainName;
    private final Observable<Server> resolverObservable;

    public DnsServerResolver(String domainName, int port) {
        this(domainName, port, DNS_LOOKUP_INTERVAL, IDLE_TIMEOUT, TimeUnit.SECONDS, Schedulers.io());
    }

    public DnsServerResolver(String domainName, int port, long reloadInterval, TimeUnit timeUnit) {
        this(domainName, port, reloadInterval, -1, timeUnit, Schedulers.io());
    }

    public DnsServerResolver(String domainName, int port, long reloadInterval, long idleTimeout, TimeUnit reloadUnit,
                             Scheduler scheduler) {
        this.domainName = domainName;
        if ("localhost".equals(domainName)) {
            this.resolverObservable = Observable.just(new Server(domainName, port));
        } else {
            this.resolverObservable = ResourceObservable.fromResource(new DnsResolverTask(port), reloadInterval,
                                                                      idleTimeout, reloadUnit, scheduler);
        }
    }

    @Override
    public Observable<Server> resolve() {
        return resolverObservable;
    }

    class DnsResolverTask implements ResourceLoader<Server> {

        private final int port;
        private boolean succeededOnce;

        public DnsResolverTask(int port) {
            this.port = port;
        }

        @Override
        public ResourceUpdate<Server> reload(Set<Server> currentSnapshot) {
            try {
                Set<Server> newAddresses = resolveEurekaServerDN();
                succeededOnce = true;
                return new ResourceUpdate<>(newAddresses, cancellationSet(currentSnapshot, newAddresses));
            } catch (NamingException e) {
                if (succeededOnce) {
                    throw new ResourceLoaderException("DNS failure on subsequent access", true, e);
                } else {
                    throw new ResourceLoaderException("Cannot resolve DNS entry on startup", false, e);
                }
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

        private Set<Server> resolveEurekaServerDN() throws NamingException {
            Hashtable<String, String> env = new Hashtable<String, String>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext dirContext = new InitialDirContext(env);
            try {
                return resolveName(dirContext, domainName);
            } finally {
                dirContext.close();
            }
        }

        private Set<Server> resolveName(DirContext dirContext, String targetDN) throws NamingException {
            while (true) {
                Attributes attrs = dirContext.getAttributes(targetDN, new String[]{"A", "CNAME"});
                Set<Server> addresses = toSetOfServerEntries(attrs, "A");
                if (!addresses.isEmpty()) {
                    return addresses;
                }
                Set<String> aliases = toSetOfString(attrs, "CNAME");
                if (aliases != null && !aliases.isEmpty()) {
                    targetDN = aliases.iterator().next();
                    continue;
                }
                return addresses;
            }
        }

        private Set<String> toSetOfString(Attributes attrs, String attrName) throws NamingException {
            Attribute attr = attrs.get(attrName);
            if (attr == null) {
                return Collections.emptySet();
            }
            Set<String> resultSet = new HashSet<>();
            NamingEnumeration<?> it = attr.getAll();
            while (it.hasMore()) {
                Object value = it.next();
                resultSet.add(value.toString());
            }
            return resultSet;
        }

        private Set<Server> toSetOfServerEntries(Attributes attrs, String attrName) throws NamingException {
            Attribute attr = attrs.get(attrName);
            if (attr == null) {
                return Collections.emptySet();
            }
            Set<Server> resultSet = new HashSet<>();
            NamingEnumeration<?> it = attr.getAll();
            while (it.hasMore()) {
                Object value = it.next();
                resultSet.add(new Server(value.toString(), port));
            }
            return resultSet;
        }
    }
}
