package com.netflix.discovery.shared;

import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;
import org.apache.http.conn.ClientConnectionRequest;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.conn.tsccm.AbstractConnPool;
import org.apache.http.impl.conn.tsccm.ConnPoolByRoute;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpParams;

/**
 * A connection manager that uses {@link NamedConnectionPool}, which provides
 * connection reuse statistics, as its underlying connection pool.
 *
 * @author awang
 *
 */
public class MonitoredConnectionManager extends ThreadSafeClientConnManager {

    public MonitoredConnectionManager(String name) {
        super();
        initMonitors(name);
    }

    public MonitoredConnectionManager(String name, SchemeRegistry schreg, long connTTL,
                                      TimeUnit connTTLTimeUnit) {
        super(schreg, connTTL, connTTLTimeUnit);
        initMonitors(name);
    }

    public MonitoredConnectionManager(String name, SchemeRegistry schreg) {
        super(schreg);
        initMonitors(name);
    }

    void initMonitors(String name) {
        if (this.pool instanceof NamedConnectionPool) {
            ((NamedConnectionPool) this.pool).initMonitors(name);
        }
    }

    @Override
    @Deprecated
    protected AbstractConnPool createConnectionPool(HttpParams params) {
        return new NamedConnectionPool(connOperator, params);
    }

    @Override
    protected ConnPoolByRoute createConnectionPool(long connTTL,
                                                   TimeUnit connTTLTimeUnit) {
        return new NamedConnectionPool(connOperator, connPerRoute, 20, connTTL, connTTLTimeUnit);
    }

    @VisibleForTesting
    ConnPoolByRoute getConnectionPool() {
        return this.pool;
    }

    @Override
    public ClientConnectionRequest requestConnection(HttpRoute route,
                                                     Object state) {
        // TODO Auto-generated method stub
        return super.requestConnection(route, state);
    }
}
