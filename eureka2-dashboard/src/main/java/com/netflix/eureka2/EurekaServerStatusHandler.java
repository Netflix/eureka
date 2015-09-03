package com.netflix.eureka2;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka2.model.instance.InstanceInfo;
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

@Singleton
public class EurekaServerStatusHandler {
    private static Logger log = LoggerFactory.getLogger(EurekaServerStatusHandler.class);

    private final BehaviorSubject<List<InstanceInfo>> registryBehaviorSubject;
    private final Gson gson;
    private RegistryCache registryCache;

    @Inject
    public EurekaServerStatusHandler(RegistryCache registryCache) {
        this.registryCache = registryCache;
        registryBehaviorSubject = BehaviorSubject.create();
        gson = new Gson();
        startStream();
    }

    private void startStream() {
        publishEurekaServerStatus();
        Observable.interval(30, TimeUnit.SECONDS).subscribe(new Action1<Long>() {
            @Override
            public void call(Long aLong) {
                publishEurekaServerStatus();
            }
        });
    }

    private void publishEurekaServerStatus() {
        final Map<String, InstanceInfo> regCache = registryCache.getCache();
        List<InstanceInfo> eurekaInstances = new ArrayList<>();
        for (Map.Entry<String, InstanceInfo> instanceInfo : regCache.entrySet()) {
            if (instanceInfo.getValue().getApp().toLowerCase().startsWith("eureka")) {
                eurekaInstances.add(instanceInfo.getValue());
            }
        }
        registryBehaviorSubject.onNext(eurekaInstances);
    }


    public Observable<Void> buildWebSocketResponse(final ObservableConnection<WebSocketFrame, WebSocketFrame> webSocketConn) {
        registryBehaviorSubject.subscribe(new Subscriber<List<InstanceInfo>>() {
            @Override
            public void onCompleted() {}

            @Override
            public void onError(Throwable e) {
                log.error("Exception in registryBehaviorSubject for eureka cluster status", e);
            }

            @Override
            public void onNext(List<InstanceInfo> instanceInfos) {
                if (!RegistryStreamUtil.sendRegistryOverWebsocket(webSocketConn, instanceInfos, gson)) {
                    this.unsubscribe();
                }
            }
        });
        return Observable.empty();
    }
}
