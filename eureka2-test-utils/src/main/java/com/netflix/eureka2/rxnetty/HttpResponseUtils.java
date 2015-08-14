package com.netflix.eureka2.rxnetty;

import javax.ws.rs.core.MediaType;
import java.nio.charset.Charset;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

/**
 * @author Tomasz Bak
 */
public final class HttpResponseUtils {
    
    private HttpResponseUtils() {
    }

    public static String handleGetRequest(int port, HttpClientRequest<ByteBuf> request, final MediaType mediaType) {
        request.getHeaders().add(Names.ACCEPT, mediaType);
        return RxNetty.createHttpClient("localhost", port).submit(request)
                .flatMap(new Func1<HttpClientResponse<ByteBuf>, Observable<String>>() {
                    @Override
                    public Observable<String> call(HttpClientResponse<ByteBuf> response) {
                        if (!response.getStatus().equals(HttpResponseStatus.OK)) {
                            return Observable.error(new Exception("invalid status code " + response.getStatus()));
                        }
                        String bodyContentType = response.getHeaders().get(Names.CONTENT_TYPE);
                        if (!mediaType.toString().equals(bodyContentType)) {
                            return Observable.error(new Exception("invalid Content-Type header in response " + bodyContentType));
                        }
                        return loadResponseBody(response);
                    }
                }).toBlocking().first();
    }

    public static Observable<String> loadResponseBody(HttpClientResponse<ByteBuf> response) {
        return response.getContent()
                .reduce(new StringBuilder(), new Func2<StringBuilder, ByteBuf, StringBuilder>() {
                    @Override
                    public StringBuilder call(StringBuilder accumulator, ByteBuf byteBuf) {
                        return accumulator.append(byteBuf.toString(Charset.defaultCharset()));
                    }
                }).map(new Func1<StringBuilder, String>() {
                    @Override
                    public String call(StringBuilder builder) {
                        return builder.toString();
                    }
                });
    }
}
