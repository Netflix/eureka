package com.netflix.discovery.shared;

import com.netflix.discovery.util.ServoUtil;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.patterns.PolledMeter;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.params.ConnPerRoute;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.conn.tsccm.BasicPoolEntry;
import org.apache.http.impl.conn.tsccm.ConnPoolByRoute;
import org.apache.http.impl.conn.tsccm.PoolEntryRequest;
import org.apache.http.impl.conn.tsccm.RouteSpecificPool;
import org.apache.http.impl.conn.tsccm.WaitingThreadAborter;
import org.apache.http.params.HttpParams;

/**
 * A connection pool that provides Servo counters to monitor the efficiency.
 * Three counters are provided: counter for getting free entries (or reusing entries),
 * counter for creating new entries, and counter for every connection request.
 *
 * @author awang
 *
 */
public class NamedConnectionPool extends ConnPoolByRoute {

    private Counter freeEntryCounter;
    private Counter createEntryCounter;
    private Counter requestCounter;
    private Counter releaseCounter;
    private Counter deleteCounter;
    private Timer requestTimer;
    private Timer creationTimer;
    private String name;

    public NamedConnectionPool(String name, ClientConnectionOperator operator,
                               ConnPerRoute connPerRoute, int maxTotalConnections, long connTTL,
                               TimeUnit connTTLTimeUnit) {
        super(operator, connPerRoute, maxTotalConnections, connTTL, connTTLTimeUnit);
        initMonitors(name);
    }

    public NamedConnectionPool(String name, ClientConnectionOperator operator,
                               ConnPerRoute connPerRoute, int maxTotalConnections) {
        super(operator, connPerRoute, maxTotalConnections);
        initMonitors(name);
    }

    public NamedConnectionPool(String name, ClientConnectionOperator operator,
                               HttpParams params) {
        super(operator, params);
        initMonitors(name);
    }

    NamedConnectionPool(ClientConnectionOperator operator,
                        ConnPerRoute connPerRoute, int maxTotalConnections, long connTTL,
                        TimeUnit connTTLTimeUnit) {
        super(operator, connPerRoute, maxTotalConnections, connTTL, connTTLTimeUnit);
    }

    NamedConnectionPool(ClientConnectionOperator operator,
                        ConnPerRoute connPerRoute, int maxTotalConnections) {
        super(operator, connPerRoute, maxTotalConnections);
    }

    NamedConnectionPool(ClientConnectionOperator operator,
                        HttpParams params) {
        super(operator, params);
    }

    void initMonitors(String name) {
        Preconditions.checkNotNull(name);
        final Registry registry = Spectator.globalRegistry();
        freeEntryCounter = registry.counter(name + "_Reuse");
        createEntryCounter = registry.counter(name + "_CreateNew");
        requestCounter = registry.counter(name + "_Request");
        releaseCounter = registry.counter(name + "_Release");
        deleteCounter = registry.counter(name + "_Delete");
        requestTimer = registry.timer(name + "_RequestConnectionTimer");
        creationTimer = registry.timer(name + "_CreateConnectionTimer");
        PolledMeter.using(registry).withName("connectionCount")
            .monitorValue(this, NamedConnectionPool::getConnectionsInPool);
        this.name = name;
    }

    @Override
    public PoolEntryRequest requestPoolEntry(HttpRoute route, Object state) {
        requestCounter.increment();
        return super.requestPoolEntry(route, state);
    }

    @Override
    protected BasicPoolEntry getFreeEntry(RouteSpecificPool rospl, Object state) {
        BasicPoolEntry entry = super.getFreeEntry(rospl, state);
        if (entry != null) {
            freeEntryCounter.increment();
        }
        return entry;
    }

    @Override
    protected BasicPoolEntry createEntry(RouteSpecificPool rospl,
                                         ClientConnectionOperator op) {
        createEntryCounter.increment();
        long monotonicTime = ServoUtil.time(creationTimer);
        try {
            return super.createEntry(rospl, op);
        } finally {
            ServoUtil.record(creationTimer, monotonicTime);
        }
    }

    @Override
    protected BasicPoolEntry getEntryBlocking(HttpRoute route, Object state,
                                              long timeout, TimeUnit tunit, WaitingThreadAborter aborter)
            throws ConnectionPoolTimeoutException, InterruptedException {
        long monotonicTime = ServoUtil.time(requestTimer);
        try {
            return super.getEntryBlocking(route, state, timeout, tunit, aborter);
        } finally {
            ServoUtil.record(requestTimer, monotonicTime);
        }
    }

    @Override
    public void freeEntry(BasicPoolEntry entry, boolean reusable,
                          long validDuration, TimeUnit timeUnit) {
        releaseCounter.increment();
        super.freeEntry(entry, reusable, validDuration, timeUnit);
    }

    @Override
    protected void deleteEntry(BasicPoolEntry entry) {
        deleteCounter.increment();
        super.deleteEntry(entry);
    }

    public final long getFreeEntryCount() {
        return freeEntryCounter.count();
    }

    public final long getCreatedEntryCount() {
        return createEntryCounter.count();
    }

    public final long getRequestsCount() {
        return requestCounter.count();
    }

    public final long getReleaseCount() {
        return releaseCounter.count();
    }

    public final long getDeleteCount() {
        return deleteCounter.count();
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
