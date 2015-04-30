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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.netflix.eureka2.codec.CodecType;
import com.netflix.eureka2.codec.avro.EurekaAvroCodec;
import com.netflix.eureka2.codec.avro.SchemaReflectData;
import com.netflix.eureka2.codec.json.EurekaJsonCodec;
import com.netflix.eureka2.protocol.Heartbeat;
import com.netflix.eureka2.protocol.discovery.AddInstance;
import com.netflix.eureka2.protocol.discovery.DeleteInstance;
import com.netflix.eureka2.protocol.discovery.InterestRegistration;
import com.netflix.eureka2.protocol.discovery.StreamStateUpdate;
import com.netflix.eureka2.protocol.discovery.UnregisterInterestSet;
import com.netflix.eureka2.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka2.protocol.registration.Register;
import com.netflix.eureka2.protocol.registration.Unregister;
import com.netflix.eureka2.protocol.replication.RegisterCopy;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.protocol.replication.UnregisterCopy;
import com.netflix.eureka2.transport.codec.AbstractNettyCodec;
import com.netflix.eureka2.transport.codec.DynamicNettyCodec;
import com.netflix.eureka2.transport.codec.EurekaCodecWrapper;
import com.netflix.eureka2.transport.utils.AvroUtils;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import org.apache.avro.Schema;
import rx.functions.Func1;

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

    /*
     * Discovery protocol constants.
     */
    static final String INTEREST_SCHEMA_FILE = "discovery-schema.avpr";
    static final String INTEREST_ENVELOPE_TYPE = "com.netflix.eureka2.protocol.discovery.DiscoveryMessage";

    static final Class<?>[] INTEREST_PROTOCOL_MODEL = {
            InterestRegistration.class, UnregisterInterestSet.class, Heartbeat.class,
            AddInstance.class, DeleteInstance.class, UpdateInstanceInfo.class, StreamStateUpdate.class
    };
    static final Set<Class<?>> INTEREST_PROTOCOL_MODEL_SET = new HashSet<>(Arrays.asList(INTEREST_PROTOCOL_MODEL));
    static final Schema INTEREST_AVRO_SCHEMA = AvroUtils.loadSchema(INTEREST_SCHEMA_FILE, INTEREST_ENVELOPE_TYPE);


    /*
     * func methods for creating protocol specific codecs
     */

    static final Func1<CodecType, AbstractNettyCodec> REGISTRATION_CODEC_FUNC = new Func1<CodecType, AbstractNettyCodec>() {
        @Override
        public AbstractNettyCodec call(CodecType codec) {
            Map<Byte, AbstractNettyCodec> map = new HashMap<>();
            map.put(CodecType.Avro.getVersion(), new EurekaCodecWrapper(new EurekaAvroCodec(REGISTRATION_PROTOCOL_MODEL_SET, REGISTRATION_AVRO_SCHEMA, new SchemaReflectData(REGISTRATION_AVRO_SCHEMA))));
            map.put(CodecType.Json.getVersion(), new EurekaCodecWrapper(new EurekaJsonCodec(REGISTRATION_PROTOCOL_MODEL_SET)));
            return new DynamicNettyCodec(REGISTRATION_PROTOCOL_MODEL_SET, Collections.unmodifiableMap(map), codec.getVersion());
        }
    };

    static final Func1<CodecType, AbstractNettyCodec> REPLICATION_CODEC_FUNC = new Func1<CodecType, AbstractNettyCodec>() {
        @Override
        public AbstractNettyCodec call(CodecType codec) {
            Map<Byte, AbstractNettyCodec> map = new HashMap<>();
            map.put(CodecType.Avro.getVersion(), new EurekaCodecWrapper(new EurekaAvroCodec(REGISTRATION_PROTOCOL_MODEL_SET, REPLICATION_AVRO_SCHEMA, new SchemaReflectData(REPLICATION_AVRO_SCHEMA))));
            map.put(CodecType.Json.getVersion(), new EurekaCodecWrapper(new EurekaJsonCodec(REGISTRATION_PROTOCOL_MODEL_SET)));
            return new DynamicNettyCodec(REPLICATION_PROTOCOL_MODEL_SET, Collections.unmodifiableMap(map), codec.getVersion());
        }
    };

    static final Func1<CodecType, AbstractNettyCodec> INTEREST_CODEC_FUNC = new Func1<CodecType, AbstractNettyCodec>() {
        @Override
        public AbstractNettyCodec call(CodecType codec) {
            Map<Byte, AbstractNettyCodec> map = new HashMap<>();
            map.put(CodecType.Avro.getVersion(), new EurekaCodecWrapper(new EurekaAvroCodec(REGISTRATION_PROTOCOL_MODEL_SET, INTEREST_AVRO_SCHEMA, new SchemaReflectData(INTEREST_AVRO_SCHEMA))));
            map.put(CodecType.Json.getVersion(), new EurekaCodecWrapper(new EurekaJsonCodec(REGISTRATION_PROTOCOL_MODEL_SET)));
            return new DynamicNettyCodec(INTEREST_PROTOCOL_MODEL_SET, Collections.unmodifiableMap(map), codec.getVersion());
        }
    };


    private EurekaTransports() {
    }

    public static PipelineConfigurator<Object, Object> registrationPipeline(CodecType codec) {
        return new EurekaPipelineConfigurator(REGISTRATION_CODEC_FUNC, codec);
    }

    public static PipelineConfigurator<Object, Object> replicationPipeline(CodecType codec) {
        return new EurekaPipelineConfigurator(REPLICATION_CODEC_FUNC, codec);
    }

    public static PipelineConfigurator<Object, Object> interestPipeline(CodecType codec) {
        return new EurekaPipelineConfigurator(INTEREST_CODEC_FUNC, codec);
    }
}