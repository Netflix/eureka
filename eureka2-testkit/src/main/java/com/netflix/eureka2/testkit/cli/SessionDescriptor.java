package com.netflix.eureka2.testkit.cli;

import java.io.PrintStream;
import java.util.Arrays;

/**
 * @author Tomasz Bak
 */
public class SessionDescriptor {

    public enum ConnectionType {Canonical, WriteCluster, WriteNode, ReadCluster, ReadNode;}

    private final ConnectionType connectionType;

    private final int nodeId;

    private SessionDescriptor(ConnectionType connectionType, int nodeId) {
        this.connectionType = connectionType;
        this.nodeId = nodeId;
    }

    public ConnectionType getConnectionType() {
        return connectionType;
    }

    public int getNodeId() {
        return nodeId;
    }

    public boolean isReadOnly() {
        return connectionType == ConnectionType.ReadNode || connectionType == ConnectionType.ReadCluster;
    }

    public void printFormatted(PrintStream out, int indentSize) {
        char[] indent = new char[indentSize];
        Arrays.fill(indent, ' ');

        out.print(indent);
        out.print("Connection type: " + connectionType);
        if (connectionType == ConnectionType.WriteNode || connectionType == ConnectionType.ReadNode) {
            out.print(", nodeId=" + nodeId);
        }
        out.println();
    }

    public static SessionDescriptor canonical() {
        return new SessionDescriptor(ConnectionType.Canonical, -1);
    }

    public static SessionDescriptor writeCluster() {
        return new SessionDescriptor(ConnectionType.WriteCluster, -1);
    }

    public static SessionDescriptor writeNode(int nodeId) {
        return new SessionDescriptor(ConnectionType.WriteNode, nodeId);
    }

    public static SessionDescriptor readCluster() {
        return new SessionDescriptor(ConnectionType.ReadCluster, -1);
    }

    public static SessionDescriptor readNode(int nodeId) {
        return new SessionDescriptor(ConnectionType.ReadNode, nodeId);
    }
}
