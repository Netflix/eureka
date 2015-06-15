/**
 * 
 */
package com.netflix.discovery;


/**
 * Class to be extended by all discovery events. Abstract as it
 * doesn't make sense for generic events to be published directly.
 * 
 * @author brenuart
 */
public abstract class DiscoveryEvent {

	/** System time when the event happened */
	private final long timestamp;
	
	
	/**
	 * Create a new DiscoveryEvent
	 */
	public DiscoveryEvent() {
		super();
		this.timestamp = System.currentTimeMillis();
	}
	
	/**
	 * Return the system time in milliseconds when the event happened.
	 */
	public final long getTimestamp() {
		return this.timestamp;
	}
}
