package com.netflix.eureka2.utils;

/**
 * Simple hostname/port holder for a server
 *
 * @author David Liu
 */
public final class Server {

    private final String host;
    private final int port;

    public Server(final String host, final int port) {
        this.port = port;
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Server)) {
            return false;
        }

        Server server = (Server) o;

        return port == server.port && !(host != null ? !host.equals(server.host) : server.host != null);

    }

    @Override
    public int hashCode() {
        int result = host != null ? host.hashCode() : 0;
        result = 31 * result + port;
        return result;
    }

    @Override
    public String toString() {
        return "Server{" +
                "host='" + host + '\'' +
                ", port=" + port +
                '}';
    }
}
