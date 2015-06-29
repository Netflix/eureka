package com.netflix.eureka2.testkit.embedded.view;

import java.io.IOException;

import com.netflix.eureka2.testkit.embedded.EurekaDeployment;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedReadCluster.ReadClusterReport;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedWriteCluster.WriteClusterReport;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedDashboardServer.DashboardServerReport;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class ClusterViewHttpServer {

    private static final Logger logger = LoggerFactory.getLogger(ClusterViewHttpServer.class);

    private static final int VIEW_PORT = 7010;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EurekaDeployment deployment;

    private HttpServer<ByteBuf, ByteBuf> httpServer;

    public ClusterViewHttpServer(EurekaDeployment deployment) {
        this.deployment = deployment;
    }

    public void start() {
        httpServer = RxNetty.createHttpServer(VIEW_PORT, new RequestHandler<ByteBuf, ByteBuf>() {
            @Override
            public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
                DeploymentReport report = createReport();
                String result;
                try {
                    result = MAPPER.writeValueAsString(report);
                } catch (IOException e) {
                    return Observable.error(e);
                }
                return response.writeStringAndFlush(result);
            }
        }).start();
    }

    public void shutdown() {
        try {
            httpServer.shutdown();
        } catch (InterruptedException e) {
            logger.error("Shutdown failure", e);
        }
    }

    private DeploymentReport createReport() {
        WriteClusterReport writeClusterReport = deployment.getWriteCluster().clusterReport();
        ReadClusterReport readClusterReport = deployment.getReadCluster().clusterReport();
        DashboardServerReport dashboardServerReport = null;
        if (deployment.getDashboardServer() != null) {
            dashboardServerReport = deployment.getDashboardServer().serverReport();
        }
        return new DeploymentReport(writeClusterReport, readClusterReport, dashboardServerReport);
    }

    public static class DeploymentReport {
        private final WriteClusterReport writeClusterReport;
        private final ReadClusterReport readClusterReport;
        private final DashboardServerReport dashboardServerReport;

        public DeploymentReport(WriteClusterReport writeClusterReport,
                                ReadClusterReport readClusterReport,
                                DashboardServerReport dashboardServerReport) {
            this.writeClusterReport = writeClusterReport;
            this.readClusterReport = readClusterReport;
            this.dashboardServerReport = dashboardServerReport;
        }

        public WriteClusterReport getWriteClusterReport() {
            return writeClusterReport;
        }

        public ReadClusterReport getReadClusterReport() {
            return readClusterReport;
        }

        public DashboardServerReport getDashboardServerReport() {
            return dashboardServerReport;
        }
    }
}
