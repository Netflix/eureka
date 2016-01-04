package com.netflix.eureka2.interests.host;

import com.netflix.eureka2.model.notification.ChangeNotification;

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

/**
 * Unit tested indirectly via DnsChangeNotificationSourceTest
 *
 * @author David Liu
 */
public class DnsResolver {

    public static Set<ChangeNotification<String>> resolveServerDN(String domainName) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
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
    private static Set<ChangeNotification<String>> resolveName(DirContext dirContext, String targetDN) throws NamingException {
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
    private static Set<ChangeNotification<String>> toSetOfServerEntries(Attributes attrs, String attrName) throws NamingException {
        Attribute attr = attrs.get(attrName);
        if (attr == null) {
            return Collections.emptySet();
        }
        Set<ChangeNotification<String>> resultSet = new HashSet<>();
        NamingEnumeration<?> it = attr.getAll();
        while (it.hasMore()) {
            Object value = it.next();
            resultSet.add(new ChangeNotification<>(ChangeNotification.Kind.Add, String.valueOf(value)));
        }
        return resultSet;
    }
}
