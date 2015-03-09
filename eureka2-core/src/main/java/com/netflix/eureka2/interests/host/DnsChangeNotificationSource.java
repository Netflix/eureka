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

package com.netflix.eureka2.interests.host;

import javax.naming.Context;
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

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.ChangeNotificationSource;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.utils.rx.ResourceObservable;
import com.netflix.eureka2.utils.rx.ResourceObservable.ResourceLoader;
import com.netflix.eureka2.utils.rx.ResourceObservable.ResourceLoaderException;
import com.netflix.eureka2.utils.rx.ResourceObservable.ResourceUpdate;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

/**
 * Change notification from DNS lookup.
 *
 * TODO: snapshot/interest based filtering not implemented yet.
 *
 * @author Tomasz Bak
 */
public class DnsChangeNotificationSource implements ChangeNotificationSource<String> {

    public static final long DNS_LOOKUP_INTERVAL = 30;
    public static final long IDLE_TIMEOUT = 300;

    private final String domainName;
    private final Observable<ChangeNotification<String>> resolverObservable;

    public DnsChangeNotificationSource(String domainName) {
        this(domainName, DNS_LOOKUP_INTERVAL, IDLE_TIMEOUT, TimeUnit.SECONDS, Schedulers.io());
    }

    public DnsChangeNotificationSource(String domainName, long reloadInterval, TimeUnit timeUnit) {
        this(domainName, reloadInterval, -1, timeUnit, Schedulers.io());
    }

    public DnsChangeNotificationSource(String domainName, long reloadInterval, long idleTimeout, TimeUnit reloadUnit,
                                       Scheduler scheduler) {
        this.domainName = domainName;
        if ("localhost".equals(domainName)) {
            this.resolverObservable = Observable.just(new ChangeNotification<>(Kind.Add, domainName));
        } else {
            this.resolverObservable = ResourceObservable.fromResource(new DnsResolverTask(), reloadInterval,
                    idleTimeout, reloadUnit, scheduler);
        }
    }

    @Override
    public Observable<String> forSnapshot(Interest<String> interest) {
        throw new IllegalStateException("operation not supported");
    }

    @Override
    public Observable<ChangeNotification<String>> forInterest(Interest<String> interest) {
        return resolverObservable;
    }

    class DnsResolverTask implements ResourceLoader<ChangeNotification<String>> {

        private final ChangeNotification<String> sentinel = ChangeNotification.bufferSentinel();
        private boolean succeededOnce;

        @Override
        public ResourceUpdate<ChangeNotification<String>> reload(Set<ChangeNotification<String>> currentSnapshot) {
            try {
                Set<ChangeNotification<String>> newAddresses = resolveServerDN();
                succeededOnce = true;
                return new ResourceUpdate<>(newAddresses, cancellationSet(currentSnapshot, newAddresses), sentinel);
            } catch (NamingException e) {
                if (succeededOnce) {
                    throw new ResourceLoaderException("DNS failure on subsequent access", true, e);
                } else {
                    throw new ResourceLoaderException("Cannot resolve DNS entry on startup", false, e);
                }
            }
        }

        private Set<ChangeNotification<String>> cancellationSet(Set<ChangeNotification<String>> currentSnapshot,
                                                                Set<ChangeNotification<String>> newAddresses) {
            Set<ChangeNotification<String>> cancelled = new HashSet<>();
            for (ChangeNotification<String> entry : currentSnapshot) {
                if (!newAddresses.contains(entry)) {
                    cancelled.add(new ChangeNotification<String>(Kind.Delete, entry.getData()));
                }
            }
            return cancelled;
        }

        private Set<ChangeNotification<String>> resolveServerDN() throws NamingException {
            Hashtable<String, String> env = new Hashtable<String, String>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            DirContext dirContext = new InitialDirContext(env);
            try {
                return resolveName(dirContext, domainName);
            } finally {
                dirContext.close();
            }
        }

        /**
         * For A records, resolve and return.
         * For CNAME, just return;
         * For TXT, assume it is a list of hostnames and/or ip addresses and return them.
         */
        private Set<ChangeNotification<String>> resolveName(DirContext dirContext, String targetDN) throws NamingException {
            Attributes attrs = dirContext.getAttributes(targetDN, new String[]{"A", "CNAME", "TXT"});
            // handle A records
            Set<ChangeNotification<String>> addresses = new HashSet<>();
            addresses.addAll(toSetOfServerEntries(attrs, "A"));
            if (!addresses.isEmpty()) {
                return addresses;
            }

            // handle CNAME
            addresses.addAll(toSetOfServerEntries(attrs, "CNAME"));
            if (!addresses.isEmpty()) {
                return addresses;
            }

            // handle TXT (assuming a list of hostnames as txt records)
            addresses.addAll(toSetOfServerEntries(attrs, "TXT"));
            if (!addresses.isEmpty()) {
                return addresses;
            }

            return addresses;
        }

        // will never return null
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

        // will never return null
        private Set<ChangeNotification<String>> toSetOfServerEntries(Attributes attrs, String attrName) throws NamingException {
            Attribute attr = attrs.get(attrName);
            if (attr == null) {
                return Collections.emptySet();
            }
            Set<ChangeNotification<String>> resultSet = new HashSet<>();
            NamingEnumeration<?> it = attr.getAll();
            while (it.hasMore()) {
                Object value = it.next();
                resultSet.add(new ChangeNotification<>(Kind.Add, String.valueOf(value)));
            }
            return resultSet;
        }
    }
}
