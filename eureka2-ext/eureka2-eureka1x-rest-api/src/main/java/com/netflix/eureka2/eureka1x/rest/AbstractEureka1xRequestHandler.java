package com.netflix.eureka2.eureka1x.rest;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.regex.Pattern;

import com.netflix.eureka2.eureka1x.rest.codec.CachingEureka1xDataCodec;
import com.netflix.eureka2.eureka1x.rest.codec.Eureka1xDataCodec;
import com.netflix.eureka2.eureka1x.rest.codec.Eureka1xDataCodec.EncodingFormat;
import com.netflix.eureka2.eureka1x.rest.codec.XStreamEureka1xDataCodec;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractEureka1xRequestHandler implements RequestHandler<ByteBuf, ByteBuf> {

    public static final String ROOT_PATH = "/eureka1x/v2";

    public static final Pattern APPS_PATH_RE = Pattern.compile("/[^/]+/v2/apps(/)?");
    public static final Pattern APPS_DELTA_PATH_RE = Pattern.compile("/[^/]+/v2/apps/delta(/)?");
    public static final Pattern APP_PATH_RE = Pattern.compile("/[^/]+/v2/apps/([^/]+)");
    public static final Pattern APP_INSTANCE_PATH_RE = Pattern.compile("/[^/]+/v2/apps/([^/]+)/([^/]+)");
    public static final Pattern VIP_PATH_RE = Pattern.compile("/[^/]+/v2/vips/([^/]+)");
    public static final Pattern SECURE_VIP_PATH_RE = Pattern.compile("/[^/]+/v2/svips/([^/]+)");
    public static final Pattern INSTANCE_PATH_RE = Pattern.compile("/[^/]+/v2/instances/([^/]+)");

    protected final SourcedEurekaRegistry<InstanceInfo> registry;
    protected final Eureka1xDataCodec codec = new CachingEureka1xDataCodec(new XStreamEureka1xDataCodec());

    protected AbstractEureka1xRequestHandler(SourcedEurekaRegistry<InstanceInfo> registry) {
        this.registry = registry;
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
}
