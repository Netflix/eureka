/**
 * 
 */
package com.netflix.discovery;

/**
 * Class to be extended by all discovery events. Abstract as it
 * doesn't make sense for generic events to be published directly.
 * 
 * @deprecated 2016-02-15 Extend from {@link AbstractEurekaEvent} instead
 */
public abstract class DiscoveryEvent extends AbstractEurekaEvent {
}
