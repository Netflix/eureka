package com.netflix.eureka2;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka2.registry.InstanceInfo;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.reactivex.netty.channel.ObservableConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.subjects.BehaviorSubject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class RegistryStream {
    private static Logger log = LoggerFactory.getLogger(RegistryStream.class);
    private final RegistryCache registryCache;
    private final Gson gson;
    private AtomicReference<List<RegistryItem>> registryItemsRef = new AtomicReference<>();
    private BehaviorSubject<List<RegistryItem>> registryBehaviorSubject;

    public static class RegistryItem {
        final String instanceId;
        final String app;
        final String vipAddress;
        private String status;

        public RegistryItem(String instanceId, String app, String vipAddress, String status) {
            this.instanceId = instanceId;
            this.app = app;
            this.vipAddress = vipAddress;
            this.status = status;
        }
    }


    @Inject
    public RegistryStream(RegistryCache registryCache) {
        this.registryCache = registryCache;
        registryBehaviorSubject = BehaviorSubject.create();
        gson = new Gson();
        startStream();
    }

    public void subscribe(final ObservableConnection<WebSocketFrame, WebSocketFrame> webSocketConn) {
        registryBehaviorSubject.subscribe(new Subscriber<List<RegistryItem>>() {
            @Override
            public void onCompleted() {
                log.info("Should not get into onCompleted");
            }

            @Override
            public void onError(Throwable e) {
                log.error("OnError in registryBehaviorSubject - ", e);
                this.unsubscribe();
            }

            @Override
            public void onNext(List<RegistryItem> registryItems) {
                try {
                    if (webSocketConn.getChannel().isOpen() && registryItems.size() > 0) {
                        final String jsonStr = gson.toJson(registryItems);
                        final ByteBuf respByteBuf = webSocketConn.getAllocator().buffer().writeBytes(jsonStr.getBytes());
                        webSocketConn.writeAndFlush(new TextWebSocketFrame(respByteBuf));
                    } else {
                        this.unsubscribe();
                    }
                } catch (Exception ex) {
                    log.error("Exception in onNext handler - ", ex.getMessage());
                }

            }
        });
    }

    private void startStream() {
        Observable.interval(1, TimeUnit.MINUTES).subscribe(new Action1<Long>() {
            @Override
            public void call(Long aLong) {
                refresh();
                registryBehaviorSubject.onNext(registryItemsRef.get());
            }
        });
    }

    private void refresh() {
        final Map<String, InstanceInfo> regCache = registryCache.getCache();
        List<RegistryItem> registryItemsCurrent = new ArrayList<>();
        for (Map.Entry<String, InstanceInfo> instanceInfo : regCache.entrySet()) {
            registryItemsCurrent.add(new RegistryItem(instanceInfo.getKey(), instanceInfo.getValue().getApp(), instanceInfo.getValue().getVipAddress(), instanceInfo.getValue().getStatus().name()));
        }

        if (isCurrentSnapshotSafeToRefresh(registryItemsCurrent.size())) {
            registryItemsRef.set(registryItemsCurrent);
        }
    }

    private boolean isCurrentSnapshotSafeToRefresh(int newSize) {
        final List<RegistryItem> registryItems = registryItemsRef.get();
        if (registryItems != null) {
            return (newSize > (registryItems.size() * 0.8));
        }
        return true;
    }
}
