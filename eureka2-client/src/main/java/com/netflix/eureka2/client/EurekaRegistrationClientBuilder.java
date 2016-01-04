/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka2.client;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.client.registration.EurekaRegistrationClientImpl;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import rx.schedulers.Schedulers;

/**
 * @author David Liu
 */
public class EurekaRegistrationClientBuilder
        extends AbstractClientBuilder<EurekaRegistrationClient, EurekaRegistrationClientBuilder> {

    private static final long RETRY_INTERVAL_MS = 5 * 1000;

    /**
     * @deprecated do not create explicitly, use {@link Eurekas#newRegistrationClientBuilder()}
     * In future releases access right to this constructor may narrow (after rc.3)
     */
    @Deprecated
    public EurekaRegistrationClientBuilder() {
    }

    @Override
    protected EurekaRegistrationClient buildClient() {
        if (serverResolver == null) {
            throw new IllegalArgumentException("Cannot build client for registration without write server resolver");
        }
        if (clientId == null) {
            clientId = Names.REGISTRATION_CLIENT;
        }

        Source clientSource = InstanceModel.getDefaultModel().createSource(Source.Origin.LOCAL, clientId);
        return new EurekaRegistrationClientImpl(clientSource, serverResolver, transportFactory, transportConfig, RETRY_INTERVAL_MS, Schedulers.computation());
    }
}
