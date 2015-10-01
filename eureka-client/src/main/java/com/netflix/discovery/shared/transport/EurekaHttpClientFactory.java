/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.discovery.shared.transport;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.wrappers.CodecWrappers;
import com.netflix.discovery.converters.wrappers.DecoderWrapper;
import com.netflix.discovery.converters.wrappers.EncoderWrapper;

/**
 * @author Tomasz Bak
 */
public interface EurekaHttpClientFactory {
    EurekaHttpClient create(String... serviceUrls);

    void shutdown();

    abstract class EurekaHttpClientFactoryBuilder<B extends EurekaHttpClientFactoryBuilder<B>> {

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

        public abstract EurekaHttpClientFactory build();

        protected B self() {
            return (B) this;
        }
    }
}
