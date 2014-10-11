package com.netflix.rx.eureka.rx;

import java.nio.charset.Charset;
import java.util.Iterator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

/**
 * HTTP server whose request/response processing is handled synchronously by an external client.
 *
 * @author Tomasz Bak
 */
public class MockHttpRxServer<I, O> {

    private PublishSubject<RequestContext<I, O>> requestObservable = PublishSubject.create();
    private ObjectTransformer<ByteBuf, I> sourceTransformer;
    private ObjectTransformer<O, ByteBuf> resultTransformer;

    private HttpServer<ByteBuf, ByteBuf> server;

    public MockHttpRxServer<I, O> withSourceTransformer(ObjectTransformer<ByteBuf, I> sourceTransformer) {
        this.sourceTransformer = sourceTransformer;
        return this;
    }

    public MockHttpRxServer<I, O> withResultTransformer(ObjectTransformer<O, ByteBuf> resultTransformer) {
        this.resultTransformer = resultTransformer;
        return this;
    }

    public MockHttpRxServer<I, O> start() {
        server = RxNetty.newHttpServerBuilder(0, new RequestHandler<ByteBuf, ByteBuf>() {
            @Override
            public Observable<Void> handle(final HttpServerRequest<ByteBuf> request, final HttpServerResponse<ByteBuf> response) {
                return request.getContent().flatMap(new Func1<ByteBuf, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(ByteBuf byteBuf) {
                        I content = null;
                        if (byteBuf.readableBytes() > 0) {
                            content = sourceTransformer.transform(byteBuf);
                        }
                        PublishSubject<O> responseSubject = PublishSubject.create();
                        RequestContext<I, O> requestContext = new RequestContext<I, O>(request, response, content, responseSubject);
                        requestObservable.onNext(requestContext);
                        return responseSubject.flatMap(new Func1<O, Observable<Void>>() {
                            @Override
                            public Observable<Void> call(O o) {
                                return response.writeAndFlush(resultTransformer.transform(o));
                            }
                        }).doOnCompleted(new Action0() {
                            @Override
                            public void call() {
                                response.close();
                            }
                        });
                    }
                });
            }
        }).enableWireLogging(LogLevel.ERROR).build().start();
        return this;
    }

    public void shutdown() throws InterruptedException {
        if (server != null) {
            server.shutdown();
        }
    }

    public Iterator<RequestContext<I, O>> contextIterator() {
        return requestObservable.toBlocking().getIterator();
    }

    public int getServerPort() {
        return server.getServerPort();
    }

    public static class RequestContext<I, O> {
        private final HttpServerRequest<ByteBuf> request;
        private final HttpServerResponse<ByteBuf> response;
        private final I content;
        private final PublishSubject<O> responseSubject;

        public RequestContext(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response,
                              I content, PublishSubject<O> responseSubject) {
            this.request = request;
            this.response = response;
            this.content = content;
            this.responseSubject = responseSubject;
        }

        public HttpServerRequest<ByteBuf> getHttpServerRequest() {
            return request;
        }

        public HttpServerResponse<ByteBuf> getHttpServerResponse() {
            return response;
        }

        public I getRequestContent() {
            return content;
        }

        public void submitResponse() {
            responseSubject.onCompleted();
        }

        public void submitResponse(O content) {
            responseSubject.onNext(content);
            responseSubject.onCompleted();
        }
    }

    public static interface ObjectTransformer<S, D> {
        D transform(S source);
    }

    public static class ToStringTransformer implements ObjectTransformer<ByteBuf, String> {
        @Override
        public String transform(ByteBuf source) {
            return source.toString(Charset.defaultCharset());
        }
    }

    public static class FromStringTransformer implements ObjectTransformer<String, ByteBuf> {
        @Override
        public ByteBuf transform(String source) {
            return UnpooledByteBufAllocator.DEFAULT.buffer().writeBytes(source.getBytes(Charset.defaultCharset()));
        }
    }
}
