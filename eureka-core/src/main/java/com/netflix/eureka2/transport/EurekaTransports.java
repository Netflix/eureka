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

package com.netflix.eureka2.transport;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.netflix.eureka2.protocol.Heartbeat;
import com.netflix.eureka2.protocol.discovery.AddInstance;
import com.netflix.eureka2.protocol.discovery.DeleteInstance;
import com.netflix.eureka2.protocol.discovery.InterestRegistration;
import com.netflix.eureka2.protocol.discovery.SnapshotComplete;
import com.netflix.eureka2.protocol.discovery.SnapshotRegistration;
import com.netflix.eureka2.protocol.discovery.UnregisterInterestSet;
import com.netflix.eureka2.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka2.protocol.registration.Register;
import com.netflix.eureka2.protocol.registration.Unregister;
import com.netflix.eureka2.protocol.replication.RegisterCopy;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.protocol.replication.UnregisterCopy;
import com.netflix.eureka2.transport.codec.avro.AvroPipelineConfigurator;
import com.netflix.eureka2.transport.codec.json.JsonPipelineConfigurator;
import com.netflix.eureka2.transport.utils.AvroUtils;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import org.apache.avro.Schema;

/**
 * Communication endpoint factory methods.
 *
 * @author Tomasz Bak
 */
public final class EurekaTransports {

    public static final int DEFAULT_REGISTRATION_PORT = 12102;
    public static final int DEFAULT_DISCOVERY_PORT = 12103;
    public static final int DEFAULT_REPLICATION_PORT = 12104;

    /*
     * Registration protocol constants.
     */
    static final String REGISTRATION_SCHEMA_FILE = "registration-schema.avpr";
    static final String REGISTRATION_ENVELOPE_TYPE = "com.netflix.eureka2.protocol.registration.RegistrationMessages";

    static final Class<?>[] REGISTRATION_PROTOCOL_MODEL = {
            Register.class, Unregister.class, Heartbeat.class
    };
    static final Set<Class<?>> REGISTRATION_PROTOCOL_MODEL_SET = new HashSet<>(Arrays.asList(REGISTRATION_PROTOCOL_MODEL));
    static final Schema REGISTRATION_AVRO_SCHEMA = AvroUtils.loadSchema(REGISTRATION_SCHEMA_FILE, REGISTRATION_ENVELOPE_TYPE);

    static final AvroPipelineConfigurator REGISTRATION_AVRO_PIPELINE_CONFIGURATOR =
            new AvroPipelineConfigurator(REGISTRATION_PROTOCOL_MODEL_SET, REGISTRATION_AVRO_SCHEMA);

    static final JsonPipelineConfigurator REGISTRATION_JSON_PIPELINE_CONFIGURATOR =
            new JsonPipelineConfigurator(REGISTRATION_PROTOCOL_MODEL_SET);

    /*
     * Replication protocol constants.
     */

    static final String REPLICATION_SCHEMA_FILE = "replication-schema.avpr";
    static final String REPLICATION_ENVELOPE_TYPE = "com.netflix.eureka2.protocol.replication.ReplicationMessages";

    static final Class<?>[] REPLICATION_PROTOCOL_MODEL = {
            ReplicationHello.class, ReplicationHelloReply.class, RegisterCopy.class, UnregisterCopy.class, Heartbeat.class
    };
    static final Set<Class<?>> REPLICATION_PROTOCOL_MODEL_SET = new HashSet<>(Arrays.asList(REPLICATION_PROTOCOL_MODEL));
    static final Schema REPLICATION_AVRO_SCHEMA = AvroUtils.loadSchema(REPLICATION_SCHEMA_FILE, REPLICATION_ENVELOPE_TYPE);

    static final AvroPipelineConfigurator REPLICATION_AVRO_PIPELINE_CONFIGURATOR =
            new AvroPipelineConfigurator(REPLICATION_PROTOCOL_MODEL_SET, REPLICATION_AVRO_SCHEMA);

    static final JsonPipelineConfigurator REPLICATION_JSON_PIPELINE_CONFIGURATOR =
            new JsonPipelineConfigurator(REPLICATION_PROTOCOL_MODEL_SET);

    /*
     * Discovery protocol constants.
     */

    static final String DISCOVERY_SCHEMA_FILE = "discovery-schema.avpr";
    static final String DISCOVERY_ENVELOPE_TYPE = "com.netflix.eureka2.protocol.discovery.DiscoveryMessage";

    static final Class<?>[] DISCOVERY_PROTOCOL_MODEL = {
            InterestRegistration.class, UnregisterInterestSet.class, Heartbeat.class,
            AddInstance.class, DeleteInstance.class, UpdateInstanceInfo.class,
            SnapshotRegistration.class, SnapshotComplete.class
    };
    static final Set<Class<?>> DISCOVERY_PROTOCOL_MODEL_SET = new HashSet<>(Arrays.asList(DISCOVERY_PROTOCOL_MODEL));
    static final Schema DISCOVERY_AVRO_SCHEMA = AvroUtils.loadSchema(DISCOVERY_SCHEMA_FILE, DISCOVERY_ENVELOPE_TYPE);

    static final AvroPipelineConfigurator DISCOVERY_AVRO_PIPELINE_CONFIGURATOR =
            new AvroPipelineConfigurator(DISCOVERY_PROTOCOL_MODEL_SET, DISCOVERY_AVRO_SCHEMA);

    static final JsonPipelineConfigurator DISCOVERY_JSON_PIPELINE_CONFIGURATOR =
            new JsonPipelineConfigurator(DISCOVERY_PROTOCOL_MODEL_SET);

    public enum Codec {
        Avro,
        Json
    }

    private EurekaTransports() {
    }

    public static PipelineConfigurator<Object, Object> registrationPipeline(Codec codec) {
        switch (codec) {
            case Avro:
                return REGISTRATION_AVRO_PIPELINE_CONFIGURATOR;
            case Json:
                return REGISTRATION_JSON_PIPELINE_CONFIGURATOR;
        }
        return failOnMissingCodec(codec);
    }

    public static PipelineConfigurator<Object, Object> replicationPipeline(Codec codec) {
        switch (codec) {
            case Avro:
                return REPLICATION_AVRO_PIPELINE_CONFIGURATOR;
            case Json:
                return REPLICATION_JSON_PIPELINE_CONFIGURATOR;
        }
        return failOnMissingCodec(codec);
    }

    public static PipelineConfigurator<Object, Object> discoveryPipeline(Codec codec) {
        switch (codec) {
            case Avro:
                return DISCOVERY_AVRO_PIPELINE_CONFIGURATOR;
            case Json:
                return DISCOVERY_JSON_PIPELINE_CONFIGURATOR;
        }
        return failOnMissingCodec(codec);
    }

    private static PipelineConfigurator<Object, Object> failOnMissingCodec(Codec codec) {
        throw new IllegalArgumentException("internal error - missing pipeline implementation for codec " + codec);
    }
}