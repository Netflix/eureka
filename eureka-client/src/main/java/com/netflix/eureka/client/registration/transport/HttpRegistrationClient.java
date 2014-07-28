package com.netflix.eureka.client.registration.transport;

import com.netflix.eureka.client.registration.RegistrationClient;
import com.netflix.eureka.protocol.registration.Register;
import com.netflix.eureka.protocol.registration.Unregister;
import com.netflix.eureka.protocol.registration.Update;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;

/**
 * FIXME It is simplified implementation diverting from real REST API, to
 * verify {@link RegistrationClient} suitability for both asynchronous and
 * synchronous API.
 *
 * @author Tomasz Bak
 */
public class HttpRegistrationClient implements RegistrationClient {

    private final HttpClient<ByteBuf, ByteBuf> httpClient;

    private final ReplaySubject<Void> statusSubject = ReplaySubject.create();

    public HttpRegistrationClient(String host, int port) {
        httpClient = RxNetty.createHttpClient(host, port);
    }

    @Override
    public Observable<Void> register(Register registryInfo) {
        return executeHttpRequest("/register", registryInfo);
    }

    @Override
    public Observable<Void> update(Update update) {
        return executeHttpRequest("/update/{id}", update);
    }

    @Override
    public Observable<Void> unregister() {
        return executeHttpRequest("/register", new Unregister());
    }

    @Override
    public void shutdown() {
        try {
            httpClient.shutdown();
        } finally {
            statusSubject.onCompleted();
        }
    }

    @Override
    public Observable<Void> lifecycleObservable() {
        return statusSubject.asObservable();
    }

    private Observable<Void> executeHttpRequest(String uri, Object content) {
        return httpClient.submit(HttpClientRequest.createPost(uri)
                .withContent(jsonFrom(content)))
                .map(new Func1<HttpClientResponse<ByteBuf>, Void>() {
                    @Override
                    public Void call(HttpClientResponse<ByteBuf> httpClientResponse) {
                        return null;
                    }
                });
    }

    private ByteBuf jsonFrom(Object registryInfo) {
        return null;
    }
}
