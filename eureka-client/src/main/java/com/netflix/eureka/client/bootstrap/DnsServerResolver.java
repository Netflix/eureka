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

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka.client.ServerResolver.ServerEntry.Action;
import rx.Scheduler;
import rx.schedulers.Schedulers;

/**
 * Load server list from DNS. Optionally, the list can be refreshed at a specified
 * interval.
 *
 * @author Tomasz Bak
 */
public class DnsServerResolver extends AbstractServerResolver<InetSocketAddress> {

    private static final long DNS_LOOKUP_INTERVAL = 30;
    private static final TimeUnit DNS_LOOKUP_INTERVAL_UNIT = TimeUnit.SECONDS;

    private final String domainName;
    private final int port;

    public DnsServerResolver(Protocol protocol, String domainName, boolean refresh) {
        this(protocol, domainName, refresh, Schedulers.io());
    }

    public DnsServerResolver(Protocol protocol, String domainName, boolean refresh, Scheduler scheduler) {
        super(refresh, scheduler);

        int port = (protocol == null) ? 0 : protocol.defaultPort();
        int idx = domainName.indexOf(':');
        if (idx == -1) {
            this.domainName = domainName;
        } else {
            this.domainName = domainName.substring(0, idx);
            port = Integer.parseInt(domainName.substring(idx + 1));
        }

        this.port = port;
    }

    @Override
    protected DnsResolverTask createResolveTask() {
        return new DnsResolverTask();
    }

    @Override
    protected IOException noServersFound(Exception ex) {
        if (ex == null) {
            return new IOException("No address available for DN entry " + domainName);
        }
        return new IOException("Could not resolve domain name " + domainName, ex);
    }

    class DnsResolverTask implements ResolverTask {
        @Override
        public void call() {
            boolean reschedule;
            try {
                reschedule = onServerListUpdate(resolveEurekaServerDN());
            } catch (Exception ex) {
                reschedule = onUpdateError(ex);
            }
            if (reschedule) {
                scheduleLookup(this, DNS_LOOKUP_INTERVAL, DNS_LOOKUP_INTERVAL_UNIT);
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
                resultSet.add(new ServerEntry<InetSocketAddress>(Action.Add, new InetSocketAddress(value.toString(), port)));
            }
            return resultSet;
        }
    }
}
