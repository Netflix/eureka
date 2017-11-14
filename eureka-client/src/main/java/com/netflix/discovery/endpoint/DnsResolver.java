package com.netflix.discovery.endpoint;

import javax.annotation.Nullable;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public final class DnsResolver {

    private static final Logger logger = LoggerFactory.getLogger(DnsResolver.class);

    private static final String DNS_PROVIDER_URL = "dns:";
    private static final String DNS_NAMING_FACTORY = "com.sun.jndi.dns.DnsContextFactory";
    private static final String JAVA_NAMING_FACTORY_INITIAL = "java.naming.factory.initial";
    private static final String JAVA_NAMING_PROVIDER_URL = "java.naming.provider.url";

    private static final String A_RECORD_TYPE = "A";
    private static final String CNAME_RECORD_TYPE = "CNAME";
    private static final String TXT_RECORD_TYPE = "TXT";

    static final DirContext dirContext = getDirContext();

    private DnsResolver() {
    }

    /**
     * Load up the DNS JNDI context provider.
     */
    public static DirContext getDirContext() {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(JAVA_NAMING_FACTORY_INITIAL, DNS_NAMING_FACTORY);
        env.put(JAVA_NAMING_PROVIDER_URL, DNS_PROVIDER_URL);

        try {
            return new InitialDirContext(env);
        } catch (Throwable e) {
            throw new RuntimeException("Cannot get dir context for some reason", e);
        }
    }

    /**
     * Resolve host name to the bottom A-Record or the latest available CNAME
     *
     * @return resolved host name
     */
    public static String resolve(String originalHost) {
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
                }
                attr = attrs.get(CNAME_RECORD_TYPE);
                if (attr != null) {
                    currentHost = attr.get().toString();
                } else {
                    targetHost = currentHost;
                }

            } while (targetHost == null);
            return targetHost;
        } catch (NamingException e) {
            logger.warn("Cannot resolve eureka server address {}; returning original value {}", currentHost, originalHost, e);
            return originalHost;
        }
    }

    /**
     * Look into A-record at a specific DNS address.
     *
     * @return resolved IP addresses or null if no A-record was present
     */
    @Nullable
    public static List<String> resolveARecord(String rootDomainName) {
        if (isLocalOrIp(rootDomainName)) {
            return null;
        }
        try {
            Attributes attrs = dirContext.getAttributes(rootDomainName, new String[]{A_RECORD_TYPE, CNAME_RECORD_TYPE});
            Attribute aRecord = attrs.get(A_RECORD_TYPE);
            Attribute cRecord = attrs.get(CNAME_RECORD_TYPE);
            if (aRecord != null && cRecord == null) {
                List<String> result = new ArrayList<>();
                NamingEnumeration<String> entries = (NamingEnumeration<String>) aRecord.getAll();
                while (entries.hasMore()) {
                    result.add(entries.next());
                }
                return result;
            }
        } catch (Exception e) {
            logger.warn("Cannot load A-record for eureka server address {}", rootDomainName, e);
            return null;
        }
        return null;
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

    /**
     * Looks up the DNS name provided in the JNDI context.
     */
    public static Set<String> getCNamesFromTxtRecord(String discoveryDnsName) throws NamingException {
        Attributes attrs = dirContext.getAttributes(discoveryDnsName, new String[]{TXT_RECORD_TYPE});
        Attribute attr = attrs.get(TXT_RECORD_TYPE);
        String txtRecord = null;
        if (attr != null) {
            txtRecord = attr.get().toString();
        }

        Set<String> cnamesSet = new TreeSet<String>();
        if (txtRecord == null || txtRecord.trim().isEmpty()) {
            return cnamesSet;
        }
        String[] cnames = txtRecord.split(" ");
        Collections.addAll(cnamesSet, cnames);
        return cnamesSet;
    }
}
