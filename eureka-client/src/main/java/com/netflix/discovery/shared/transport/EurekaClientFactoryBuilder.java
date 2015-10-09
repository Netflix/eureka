package com.netflix.discovery.shared.transport;

import com.netflix.appinfo.AbstractEurekaIdentity;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.wrappers.CodecWrappers;
import com.netflix.discovery.converters.wrappers.DecoderWrapper;
import com.netflix.discovery.converters.wrappers.EncoderWrapper;

/**
 * @author Tomasz Bak
 */
public abstract class EurekaClientFactoryBuilder<F, B extends EurekaClientFactoryBuilder<F, B>> {

    protected InstanceInfo myInstanceInfo;
    protected boolean allowRedirect;
    protected boolean systemSSL;
    protected String clientName;
    protected int maxConnectionsPerHost;
    protected int maxTotalConnections;
    protected String trustStoreFileName;
    protected String trustStorePassword;
    protected String userAgent;
    protected String proxyUserName;
    protected String proxyPassword;
    protected String proxyHost;
    protected int proxyPort;
    protected int connectionTimeout;
    protected int readTimeout;
    protected int connectionIdleTimeout;
    protected EncoderWrapper encoderWrapper;
    protected DecoderWrapper decoderWrapper;
    protected AbstractEurekaIdentity clientIdentity;

    public B withMyInstanceInfo(InstanceInfo myInstanceInfo) {
        this.myInstanceInfo = myInstanceInfo;
        return self();
    }

    public B withClientName(String clientName) {
        this.clientName = clientName;
        return self();
    }

    public B withUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return self();
    }

    public B withAllowRedirect(boolean allowRedirect) {
        this.allowRedirect = allowRedirect;
        return self();
    }

    public B withConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        return self();
    }

    public B withReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
        return self();
    }

    public B withConnectionIdleTimeout(int connectionIdleTimeout) {
        this.connectionIdleTimeout = connectionIdleTimeout;
        return self();
    }

    public B withMaxConnectionsPerHost(int maxConnectionsPerHost) {
        this.maxConnectionsPerHost = maxConnectionsPerHost;
        return self();
    }

    public B withMaxTotalConnections(int maxTotalConnections) {
        this.maxTotalConnections = maxTotalConnections;
        return self();
    }

    public B withProxy(String proxyHost, int proxyPort, String user, String password) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxyUserName = user;
        this.proxyPassword = password;
        return self();
    }

    public B withSystemSSLConfiguration() {
        this.systemSSL = true;
        return self();
    }

    public B withTrustStoreFile(String trustStoreFileName, String trustStorePassword) {
        this.trustStoreFileName = trustStoreFileName;
        this.trustStorePassword = trustStorePassword;
        return self();
    }

    public B withEncoder(String encoderName) {
        return this.withEncoderWrapper(CodecWrappers.getEncoder(encoderName));
    }

    public B withEncoderWrapper(EncoderWrapper encoderWrapper) {
        this.encoderWrapper = encoderWrapper;
        return self();
    }

    public B withDecoder(String decoderName, String clientDataAccept) {
        return this.withDecoderWrapper(CodecWrappers.resolveDecoder(decoderName, clientDataAccept));
    }

    public B withDecoderWrapper(DecoderWrapper decoderWrapper) {
        this.decoderWrapper = decoderWrapper;
        return self();
    }

    public B withClientIdentity(AbstractEurekaIdentity clientIdentity) {
        this.clientIdentity = clientIdentity;
        return self();
    }

    public abstract F build();

    @SuppressWarnings("unchecked")
    protected B self() {
        return (B) this;
    }
}
