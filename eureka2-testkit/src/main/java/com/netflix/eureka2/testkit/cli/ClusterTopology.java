package com.netflix.eureka2.testkit.cli;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import com.netflix.eureka2.server.resolver.ClusterAddress;

/**
 * @author Tomasz Bak
 */
public class ClusterTopology {

    private final List<ClusterAddress> writeServers;
    private final List<ClusterAddress> readServers;
    private final String readClusterVip;

    public ClusterTopology(List<ClusterAddress> writeServers, List<ClusterAddress> readServers, String readClusterVip) {
        this.writeServers = writeServers;
        this.readServers = readServers;
        this.readClusterVip = readClusterVip;
    }

    public List<ClusterAddress> getWriteServers() {
        return writeServers;
    }

    public List<ClusterAddress> getReadServers() {
        return readServers;
    }

    public String getReadClusterVip() {
        return readClusterVip;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ClusterTopology that = (ClusterTopology) o;

        if (writeServers != null ? !writeServers.equals(that.writeServers) : that.writeServers != null)
            return false;
        return !(readServers != null ? !readServers.equals(that.readServers) : that.readServers != null);
    }

    @Override
    public int hashCode() {
        int result = writeServers != null ? writeServers.hashCode() : 0;
        result = 31 * result + (readServers != null ? readServers.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ClusterTopology{" +
                "writeServers=" + writeServers +
                ", readServers=" + readServers +
                '}';
    }

    public void printFormatted(PrintStream out, int indentSize) {
        char[] indent = new char[indentSize];
        Arrays.fill(indent, ' ');
        if (writeServers != null && !writeServers.isEmpty()) {
            out.print(indent);
            out.println("write servers:");
            printServers(writeServers, out, indent);
        }
        if (readServers != null && !readServers.isEmpty()) {
            out.print(indent);
            out.println("read servers:");
            printServers(readServers, out, indent);
        }
    }

    private static void printServers(List<ClusterAddress> servers, PrintStream out, char[] indent) {
        for (int i = 0; i < servers.size(); i++) {
            ClusterAddress clusterAddress = servers.get(i);
            out.print(indent);
            out.print(indent);
            out.print("[" + i + "] ");
            out.println(clusterAddress);
        }
    }
}
