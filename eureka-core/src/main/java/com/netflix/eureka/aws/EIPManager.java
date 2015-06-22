package com.netflix.eureka.aws;

import java.util.Collection;

/**
 * An AWS specific <em>elastic ip</em> binding utility for binding eureka
 * servers for a well known <code>IP address</code>.
 *
 * <p>
 * <em>Eureka</em> clients talk to <em>Eureka</em> servers bound with well known
 * <code>IP addresses</code> since that is the most reliable mechanism to
 * discover the <em>Eureka</em> servers. When Eureka servers come up they bind
 * themselves to a well known <em>elastic ip</em>
 * </p>
 *
 * <p>
 * This binding mechanism gravitates towards one eureka server per zone for
 * resilience.Atleast one elastic ip should be slotted for each eureka server in
 * a zone. If more than eureka server is launched per zone and there are not
 * enough elastic ips slotted, the server tries to pick a free EIP slotted for other
 * zones and if it still cannot find a free EIP, waits and keeps trying.
 * </p>
 *
 * @author Karthik Ranganathan, Greg Kim
 */
public interface EIPManager {

    /**
     * Bind EIP and start monitoring process.
     */
    void start();

    /**
     * Unbind EIP and stop the monitoring process.
     */
    void stop();

    /**
     * Checks if an EIP is already bound to the instance.
     * @return true if an EIP is bound, false otherwise
     */
    boolean isEIPBound();

    /**
     * Checks if an EIP is bound and optionally binds the EIP.
     *
     * The list of EIPs are arranged with the EIPs allocated in the zone first
     * followed by other EIPs.
     *
     * If an EIP is already bound to this instance this method simply returns. Otherwise, this method tries to find
     * an unused EIP based on information from AWS. If it cannot find any unused EIP this method, it will be retried
     * for a specified interval.
     *
     * One of the following scenarios can happen here :
     *
     *  1) If the instance is already bound to an EIP as deemed by AWS, no action is taken.
     *  2) If an EIP is already bound to another instance as deemed by AWS, that EIP is skipped.
     *  3) If an EIP is not already bound to an instance and if this instance is not bound to an EIP, then
     *     the EIP is bound to this instance.
     */
    void bindEIP();

    /**
     * Unbind the EIP that this instance is associated with.
     */
    void unbindEIP();

    /**
     * Get the list of EIPs in the order of preference depending on instance zone.
     *
     * @param myInstanceId
     *            the instance id for this instance
     * @param myZone
     *            the zone where this instance is in
     * @return Collection containing the list of available EIPs
     */
    Collection<String> getCandidateEIPs(String myInstanceId, String myZone);
}
