package com.netflix.eureka2.testkit.netrouter;

import java.util.concurrent.TimeUnit;

import rx.Observable;

/**
 *  A link between two servers with capabilities to inject different transmission properties
 * (throughput, data loss, etc).
 *
 * @author Tomasz Bak
 */
public interface NetworkLink {

    enum BandwidthUnit {Bits, Kb, Mb, Gb}

    boolean isUp();

    /**
     * Connect two endpoints.
     *
     * @return true if the connection actually happened (endpoints where disconnected prior to calling this method)
     */
    Observable<Void> connect();

    /**
     * Convenience method that blocks until connect operation is completed.
     */
    void connect(long timeout, TimeUnit timeUnit);

    /**
     * Disconnect two endpoints.
     *
     * @return true if the disconnect actually happened (endpoints where connected prior to calling this method)
     */
    Observable<Void> disconnect();

    /**
     * Convenience method that blocks until disconnect operation is completed.
     */
    void disconnect(long timeout, TimeUnit timeUnit);

    /**
     * Limit link bandwidth to a given throughput, queueing up excessive data.
     */
    void limitBandwidthTo(int throughput, BandwidthUnit bandwidthUnit);

    /**
     * Do not impose any constraints on link throughput.
     *
     * @return true if the link's throughput was previously constraint
     */
    boolean openUnlimitedBandwidth();

    /**
     * Fixed link latency.
     */
    void injectLatency(long time, TimeUnit timeUnit);

    /**
     * Latency with variability limited by jitterAmplitude.
     */
    void injectLatency(long time, long jitterAmplitude, TimeUnit timeUnit);
}
