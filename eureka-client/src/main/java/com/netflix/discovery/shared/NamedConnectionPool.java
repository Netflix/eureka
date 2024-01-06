package com.netflix.discovery.shared;

import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;
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
        freeEntryCounter = Monitors.newCounter(name + "_Reuse");
        createEntryCounter = Monitors.newCounter(name + "_CreateNew");
        requestCounter = Monitors.newCounter(name + "_Request");
        releaseCounter = Monitors.newCounter(name + "_Release");
        deleteCounter = Monitors.newCounter(name + "_Delete");
        requestTimer = Monitors.newTimer(name + "_RequestConnectionTimer", TimeUnit.MILLISECONDS);
        creationTimer = Monitors.newTimer(name + "_CreateConnectionTimer", TimeUnit.MILLISECONDS);
        this.name = name;
        Monitors.registerObject(name, this);
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
        Stopwatch stopWatch = creationTimer.start();
        try {
            return super.createEntry(rospl, op);
        } finally {
            stopWatch.stop();
        }
    }

    @Override
    protected BasicPoolEntry getEntryBlocking(HttpRoute route, Object state,
                                              long timeout, TimeUnit tunit, WaitingThreadAborter aborter)
            throws ConnectionPoolTimeoutException, InterruptedException {
        Stopwatch stopWatch = requestTimer.start();
        try {
            return super.getEntryBlocking(route, state, timeout, tunit, aborter);
        } finally {
            stopWatch.stop();
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
        return freeEntryCounter.getValue().longValue();
    }

    public final long getCreatedEntryCount() {
        return createEntryCounter.getValue().longValue();
    }

    public final long getRequestsCount() {
        return requestCounter.getValue().longValue();
    }

    public final long getReleaseCount() {
        return releaseCounter.getValue().longValue();
    }

    public final long getDeleteCount() {
        return deleteCounter.getValue().longValue();
    }

    @Monitor(name = "connectionCount", type = DataSourceType.GAUGE)
    public int getConnectionCount() {
        return this.getConnectionsInPool();
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if(Monitors.isObjectRegistered(name, this)) {
            Monitors.unregisterObject(name, this);
        }
    }
}
