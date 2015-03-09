package com.netflix.eureka2.eureka1.rest;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Pattern;

import com.netflix.eureka2.eureka1.rest.codec.CachingEureka1DataCodec;
import com.netflix.eureka2.eureka1.rest.codec.Eureka1DataCodec;
import com.netflix.eureka2.eureka1.rest.codec.Eureka1DataCodec.EncodingFormat;
import com.netflix.eureka2.eureka1.rest.codec.XStreamEureka1DataCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractEureka1RequestHandler implements RequestHandler<ByteBuf, ByteBuf> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractEureka1RequestHandler.class);

    public static final String ROOT_PATH = "/eureka1/v2";

    public static final Pattern APPS_PATH_RE = Pattern.compile("/[^/]+/v2/apps(/)?");
    public static final Pattern APPS_DELTA_PATH_RE = Pattern.compile("/[^/]+/v2/apps/delta(/)?");
    public static final Pattern APP_PATH_RE = Pattern.compile("/[^/]+/v2/apps/([^/]+)");
    public static final Pattern APP_INSTANCE_PATH_RE = Pattern.compile("/[^/]+/v2/apps/([^/]+)/([^/]+)");
    public static final Pattern APP_INSTANCE_META_PATH_RE = Pattern.compile("/[^/]+/v2/apps/([^/]+)/([^/]+)/metadata");
    public static final Pattern VIP_PATH_RE = Pattern.compile("/[^/]+/v2/vips/([^/]+)");
    public static final Pattern SECURE_VIP_PATH_RE = Pattern.compile("/[^/]+/v2/svips/([^/]+)");
    public static final Pattern INSTANCE_PATH_RE = Pattern.compile("/[^/]+/v2/instances/([^/]+)");

    protected final Eureka1DataCodec codec = new CachingEureka1DataCodec(new XStreamEureka1DataCodec());

    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        try {
            return dispatch(request, response);
        } catch (Exception e) {
            logger.error("Error during handling {} request {}", request.getHttpMethod(), request.getPath());
            if (logger.isDebugEnabled()) {
                logger.debug("Request handling error", e);
            }
            response.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
            return Observable.empty();
        }
    }

    protected abstract Observable<Void> dispatch(HttpServerRequest<ByteBuf> request,
                                                 HttpServerResponse<ByteBuf> response) throws Exception;

    protected static EncodingFormat getRequestFormat(HttpServerRequest<ByteBuf> request) throws IOException {
        String acceptHeader = request.getHeaders().get(Names.ACCEPT);
        if (acceptHeader == null) {
            return EncodingFormat.Json; // Default to JSON if nothing specified
        }
        MediaType mediaType;
        try {
            mediaType = MediaType.valueOf(acceptHeader);
            if (mediaType.equals(MediaType.APPLICATION_JSON_TYPE)) {
                return EncodingFormat.Json;
            }
            if (mediaType.equals(MediaType.APPLICATION_XML_TYPE)) {
                return EncodingFormat.Xml;
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Unsupported content type " + acceptHeader, e);
        }
        throw new IOException("Only JSON and XML encodings are supported, and requested " + acceptHeader);
    }

    protected static boolean isGzipEncoding(HttpServerRequest<ByteBuf> request) {
        String acceptEncoding = request.getHeaders().get(Names.ACCEPT_ENCODING);
        return acceptEncoding != null && acceptEncoding.contains("gzip");
    }

    protected Observable<Void> encodeResponse(EncodingFormat format, boolean gzip, HttpServerResponse<ByteBuf> response, Object entity) throws IOException {
        response.getHeaders().add(Names.CONTENT_TYPE, format == EncodingFormat.Json ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_XML);
        if (gzip) {
            response.getHeaders().add(Names.CONTENT_ENCODING, "gzip");
        }
        byte[] encodedBody = codec.encode(entity, format, gzip);
        return response.writeAndFlush(Unpooled.wrappedBuffer(encodedBody));
    }

    protected <T> Observable<T> decodeBody(final EncodingFormat format, final HttpServerRequest<ByteBuf> request, final Class<T> bodyType) throws IOException {
        return request.getContent().flatMap(new Func1<ByteBuf, Observable<ByteBuf>>() {
            @Override
            public Observable<ByteBuf> call(ByteBuf byteBuf) {
                return Observable.just(byteBuf);
            }
        }).reduce(new ByteArrayOutputStream(), new Func2<ByteArrayOutputStream, ByteBuf, ByteArrayOutputStream>() {
            @Override
            public ByteArrayOutputStream call(ByteArrayOutputStream accumulator, ByteBuf byteBuf) {
                while (byteBuf.readableBytes() > 0) {
                    accumulator.write(byteBuf.readByte());
                }
                return accumulator;
            }
        }).flatMap(new Func1<ByteArrayOutputStream, Observable<T>>() {
            @Override
            public Observable<T> call(ByteArrayOutputStream os) {
                try {
                    return Observable.just(codec.decode(os.toByteArray(), bodyType, format));
                } catch (IOException e) {
                    logger.error("Cannot decode POST {} request body as {} type in {} encoding", request.getPath(), bodyType, format);
                    return Observable.error(e);
                }
            }
        });
    }

    protected Observable<Void> returnInvalidUrl(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        logger.info("Invalid request URL {} {}", request.getHttpMethod(), request.getPath());
        response.setStatus(HttpResponseStatus.NOT_FOUND);
        return Observable.empty();
    }
}
