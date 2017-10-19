package com.netflix.discovery.shared.transport.jersey;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.protocol.HttpContext;

/**
 * Adapts a version 4.3+ {@link SSLConnectionSocketFactory} to a pre 4.3
 * {@link SSLSocketFactory}. This allows {@link HttpClient}s built using the
 * deprecated pre 4.3 APIs to use SSL improvements from 4.3, e.g. SNI.
 *
 * @author William Tran
 *
 */
public class SSLSocketFactoryAdapter extends SSLSocketFactory {

    private final SSLConnectionSocketFactory factory;

    public SSLSocketFactoryAdapter(SSLConnectionSocketFactory factory) {
        // super's dependencies are dummies, and will delegate all calls to the
        // to the overridden methods
        super(DummySSLSocketFactory.INSTANCE, DummyX509HostnameVerifier.INSTANCE);
        this.factory = factory;
    }
    
    public SSLSocketFactoryAdapter(SSLConnectionSocketFactory factory, HostnameVerifier hostnameVerifier) {
        super(DummySSLSocketFactory.INSTANCE, new WrappedX509HostnameVerifier(hostnameVerifier));
        this.factory = factory;
    }

    @Override
    public Socket createSocket(final HttpContext context) throws IOException {
        return factory.createSocket(context);
    }

    @Override
    public Socket connectSocket(
            final int connectTimeout,
            final Socket socket,
            final HttpHost host,
            final InetSocketAddress remoteAddress,
            final InetSocketAddress localAddress,
            final HttpContext context) throws IOException {
        return factory.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
    }

    @Override
    public Socket createLayeredSocket(
            final Socket socket,
            final String target,
            final int port,
            final HttpContext context) throws IOException {
        return factory.createLayeredSocket(socket, target, port, context);
    }

    private static class DummySSLSocketFactory extends javax.net.ssl.SSLSocketFactory {
        private static final DummySSLSocketFactory INSTANCE = new DummySSLSocketFactory();

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public String[] getDefaultCipherSuites() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
                throws IOException, UnknownHostException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            throw new UnsupportedOperationException();
        }
    }

    private static class DummyX509HostnameVerifier implements X509HostnameVerifier {
        private static final DummyX509HostnameVerifier INSTANCE = new DummyX509HostnameVerifier();

        @Override
        public boolean verify(String hostname, SSLSession session) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void verify(String host, SSLSocket ssl) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void verify(String host, X509Certificate cert) throws SSLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void verify(String host, String[] cns, String[] subjectAlts) throws SSLException {
            throw new UnsupportedOperationException();
        }

    }
    
    private static class WrappedX509HostnameVerifier extends DummyX509HostnameVerifier {
        HostnameVerifier hostnameVerifier;
        private WrappedX509HostnameVerifier(HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = hostnameVerifier;
        }

        @Override
        public boolean verify(String hostname, SSLSession session) {
            return hostnameVerifier.verify(hostname, session);
        }
    }

}
