package com.netflix.discovery.util;

import javax.naming.NamingException;
import java.util.Set;

/**
 * @author Tomasz Bak
 */
public interface DnsResolver {

    /**
     * Resolve host part of the given URI to the bottom A-Record or the latest available CNAME
     *
     * @return URI identical to the one provided, with host name swapped with the resolved value
     */
    String resolve(String originalHost);

    /**
     * Looks up the DNS name provided in the JNDI context.
     */
    Set<String> getCNamesFromTxtRecord(String discoveryDnsName) throws NamingException;
}
