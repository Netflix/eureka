package netflix.adminresources.resources;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka2.server.config.EurekaCommonConfig;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.protocol.http.websocket.WebSocketClient;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
@Singleton
public class StatusRegistry {

    private static final Logger logger = LoggerFactory.getLogger(StatusRegistry.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final int port;
    private WebSocketClient<TextWebSocketFrame, TextWebSocketFrame> wsClient;

    private final Map<String, GenericHealthStatusUpdate> statusMap = new ConcurrentHashMap<>();
    private volatile GenericHealthStatusUpdate aggregated;

    @Inject
    public StatusRegistry(EurekaCommonConfig config) {
        this.port = config.getHttpPort();
    }

    public StatusRegistry(int port) {
        this.port = port;
    }

    @PostConstruct
    public void start() {
        wsClient = RxNetty.<TextWebSocketFrame, TextWebSocketFrame>newWebSocketClientBuilder("localhost", port).withWebSocketURI("/healthcheck").build();
        wsClient.connect().flatMap(new Func1<ObservableConnection<TextWebSocketFrame, TextWebSocketFrame>, Observable<TextWebSocketFrame>>() {
            @Override
            public Observable<TextWebSocketFrame> call(ObservableConnection<TextWebSocketFrame, TextWebSocketFrame> connection) {
                return connection.getInput();
            }
        }).subscribe(new Subscriber<TextWebSocketFrame>() {
            @Override
            public void onCompleted() {
                logger.info("HealthStatus WebSocket stream closed");
            }

            @Override
            public void onError(Throwable e) {
                logger.error("HealthStatus WebSocket stream terminate with an error", e);
            }

            @Override
            public void onNext(TextWebSocketFrame wsFrame) {
                updateHealthStatus(wsFrame);
            }
        });
    }

    @PreDestroy
    public void stop() {
        if (wsClient != null) {
            wsClient.shutdown();
            wsClient = null;
        }
    }

    public List<GenericHealthStatusUpdate> get() {
        return new ArrayList<>(statusMap.values());
    }

    public GenericHealthStatusUpdate getAggregated() {
        return aggregated;
    }

    public int size() {
        return statusMap.size();
    }

    private void updateHealthStatus(TextWebSocketFrame wsFrame) {
        try {
            GenericHealthStatusUpdate item = mapper.readValue(wsFrame.text(), GenericHealthStatusUpdate.class);
            if (item.getDescriptor().getClassName().endsWith("Aggregator")) {
                aggregated = item;
            } else {
                statusMap.put(item.getDescriptor().getClassName(), item);
            }
        } catch (IOException e) {
            logger.warn("Invalid WebSocket message: {}", wsFrame.text());
            if (logger.isDebugEnabled()) {
                logger.debug("Invalid WebSocket message", e);
            }
        }
    }
}
