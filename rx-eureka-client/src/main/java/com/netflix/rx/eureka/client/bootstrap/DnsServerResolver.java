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

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
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
 * Load server list from DNS. Optionally, the list can be refreshed at a specified
 * interval.
 *
 * @author Tomasz Bak
 */
public class DnsServerResolver implements ServerResolver<InetSocketAddress> {

    private static final long DNS_LOOKUP_INTERVAL = 30;
    private static final long IDLE_TIMEOUT = 300;

    private final String domainName;
    private final Set<Protocol> protocols;
    private final Observable<ServerEntry<InetSocketAddress>> resolverObservable;

    public DnsServerResolver(String domainName, Set<Protocol> protocols) {
        this(domainName, protocols, DNS_LOOKUP_INTERVAL, IDLE_TIMEOUT, TimeUnit.SECONDS, Schedulers.io());
    }

    public DnsServerResolver(String domainName, Set<Protocol> protocols, long reloadInterval, TimeUnit timeUnit) {
        this(domainName, protocols, reloadInterval, -1, timeUnit, Schedulers.io());
    }

    public DnsServerResolver(String domainName, Set<Protocol> protocols, long reloadInterval, long idleTimeout, TimeUnit reloadUnit, Scheduler scheduler) {
        this.domainName = domainName;
        this.protocols = protocols;
        if ("localhost".equals(domainName)) {
            this.resolverObservable = Observable.just(new ServerEntry<InetSocketAddress>(Action.Add, new InetSocketAddress(domainName, 0), protocols));
        } else {
            this.resolverObservable = ResourceObservable.fromResource(new DnsResolverTask(), reloadInterval, idleTimeout, reloadUnit, scheduler);
        }
    }

    @Override
    public Observable<ServerEntry<InetSocketAddress>> resolve() {
        return resolverObservable;
    }

    class DnsResolverTask implements ResourceLoader<ServerEntry<InetSocketAddress>> {

        private boolean succeededOnce;

        @Override
        public ResourceUpdate<ServerEntry<InetSocketAddress>> reload(Set<ServerEntry<InetSocketAddress>> currentSnapshot) {
            try {
                Set<ServerEntry<InetSocketAddress>> newAddresses = resolveEurekaServerDN();
                succeededOnce = true;
                return new ResourceUpdate<>(newAddresses, ServerEntry.cancellationSet(currentSnapshot, newAddresses));
            } catch (NamingException e) {
                if (succeededOnce) {
                    throw new ResourceLoaderException("DNS failure on subsequent access", true, e);
                } else {
                    throw new ResourceLoaderException("Cannot resolve DNS entry on startup", false, e);
                }
            }
        }

        private Set<ServerEntry<InetSocketAddress>> resolveEurekaServerDN() throws NamingException {
            Hashtable<String, String> env = new Hashtable<String, String>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext dirContext = new InitialDirContext(env);
            try {
                return resolveName(dirContext, domainName);
            } finally {
                dirContext.close();
            }
        }

        private Set<ServerEntry<InetSocketAddress>> resolveName(DirContext dirContext, String targetDN) throws NamingException {
            while (true) {
                Attributes attrs = dirContext.getAttributes(targetDN, new String[]{"A", "CNAME"});
                Set<ServerEntry<InetSocketAddress>> addresses = toSetOfServerEntries(attrs, "A");
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

        private Set<ServerEntry<InetSocketAddress>> toSetOfServerEntries(Attributes attrs, String attrName) throws NamingException {
            Attribute attr = attrs.get(attrName);
            if (attr == null) {
                return Collections.emptySet();
            }
            Set<ServerEntry<InetSocketAddress>> resultSet = new HashSet<>();
            NamingEnumeration<?> it = attr.getAll();
            while (it.hasMore()) {
                Object value = it.next();
                resultSet.add(new ServerEntry<InetSocketAddress>(Action.Add, new InetSocketAddress(value.toString(), 0), protocols));
            }
            return resultSet;
        }
    }
}
