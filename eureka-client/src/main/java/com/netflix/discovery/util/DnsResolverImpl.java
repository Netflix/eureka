package com.netflix.discovery.util;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public final class DnsResolverImpl implements DnsResolver {

    private static final Logger logger = LoggerFactory.getLogger(DnsResolverImpl.class);

    private static final String DNS_PROVIDER_URL = "dns:";
    private static final String DNS_NAMING_FACTORY = "com.sun.jndi.dns.DnsContextFactory";
    private static final String JAVA_NAMING_FACTORY_INITIAL = "java.naming.factory.initial";
    private static final String JAVA_NAMING_PROVIDER_URL = "java.naming.provider.url";

    private static final String A_RECORD_TYPE = "A";
    private static final String CNAME_RECORD_TYPE = "CNAME";
    private static final String TXT_RECORD_TYPE = "TXT";

    private final DirContext dirContext;

    public DnsResolverImpl() {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(JAVA_NAMING_FACTORY_INITIAL, DNS_NAMING_FACTORY);
        env.put(JAVA_NAMING_PROVIDER_URL, DNS_PROVIDER_URL);

        try {
            dirContext = new InitialDirContext(env);
        } catch (Throwable e) {
            throw new RuntimeException("Cannot get dir context for some reason", e);
        }
    }

    /**
     * Resolve host part of the given URI to the bottom A-Record or the latest available CNAME
     *
     * @return URI identical to the one provided, with host name swapped with the resolved value
     */
    @Override
    public String resolve(String originalHost) {
        String currentHost = originalHost;
        if (isLocalOrIp(currentHost)) {
            return originalHost;
        }
        try {
            String targetHost = null;
            do {
                Attributes attrs = dirContext.getAttributes(currentHost, new String[]{A_RECORD_TYPE, CNAME_RECORD_TYPE});
                Attribute attr = attrs.get(A_RECORD_TYPE);
                if (attr != null) {
                    targetHost = attr.get().toString();
                } else {
                    attr = attrs.get(CNAME_RECORD_TYPE);
                    if (attr != null) {
                        currentHost = attr.get().toString();
                    } else {
                        targetHost = currentHost;
                    }
                }
            } while (targetHost == null);
            return targetHost;
        } catch (NamingException e) {
            logger.warn("Cannot resolve discovery server address " + currentHost + "; returning original value " + originalHost, e);
            return originalHost;
        }
    }

    /**
     * Looks up the DNS name provided in the JNDI context.
     * <p>
     * The block of addresses can be delivered as a single text value with space separated domain names or
     * as a list of entries. This methods handles properly both cases.
     */
    @Override
    public Set<String> getCNamesFromTxtRecord(String discoveryDnsName) throws NamingException {
        Attributes attrs = dirContext.getAttributes(discoveryDnsName, new String[]{TXT_RECORD_TYPE});
        Attribute attr = attrs.get(TXT_RECORD_TYPE);

        if (attr == null) {
            return Collections.emptySet();
        }

        Set<String> cnamesSet = new TreeSet<String>();
        if (attr.size() > 1) {
            NamingEnumeration<?> attrEn = attr.getAll();
            while (attrEn.hasMore()) {
                cnamesSet.add((String) attrEn.next());
            }
        } else {
            String txtRecord = attr.get().toString().trim();
            if (txtRecord.trim().isEmpty()) {
                return Collections.emptySet();
            }
            String[] cnames = txtRecord.split(" ");
            Collections.addAll(cnamesSet, cnames);
        }
        return cnamesSet;
    }

    private static boolean isLocalOrIp(String currentHost) {
        if ("localhost".equals(currentHost)) {
            return true;
        }
        if ("127.0.0.1".equals(currentHost)) {
            return true;
        }
        return false;
    }
}
