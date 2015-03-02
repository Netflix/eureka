package com.netflix.eureka2.server.http.proxy;

/**
 * @author Tomasz Bak
 */
public abstract class ForwardingRule {

    private final int port;

    protected ForwardingRule(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    public abstract boolean matches(String path);

    public static ForwardingRule pathPrefix(int port, final String pathPrefix) {
        return new ForwardingRule(port) {
            @Override
            public boolean matches(String path) {
                return path.startsWith(pathPrefix);
            }
        };
    }

    public static ForwardingRule any(int port) {
        return new ForwardingRule(port) {
            @Override
            public boolean matches(String path) {
                return true;
            }
        };
    }
}
