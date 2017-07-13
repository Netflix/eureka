package com.netflix.discovery.converters;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.RuntimeJsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.ActionType;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.InstanceInfo.PortType;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.DeserializerStringCache;
import com.netflix.discovery.util.DeserializerStringCache.CacheScope;

import vlsi.utils.CompactHashMap;

/**
 * @author Tomasz Bak
 * @author Spencer Gibb
 */
public class EurekaJacksonCodec {

    private static final Logger logger = LoggerFactory.getLogger(EurekaJacksonCodec.class);
    private static final Version VERSION = new Version(1, 1, 0, null);
    public static final String NODE_LEASE = "leaseInfo";
    public static final String NODE_METADATA = "metadata";
    public static final String NODE_DATACENTER = "dataCenterInfo";
    public static final String NODE_APP = "application";

    protected static final String ELEM_INSTANCE = "instance";
    protected static final String ELEM_OVERRIDDEN_STATUS = "overriddenStatus";
    protected static final String ELEM_HOST = "hostName";
    protected static final String ELEM_INSTANCE_ID = "instanceId";
    protected static final String ELEM_APP = "app";
    protected static final String ELEM_IP = "ipAddr";
    protected static final String ELEM_SID = "sid";
    protected static final String ELEM_STATUS = "status";
    protected static final String ELEM_PORT = "port";
    protected static final String ELEM_SECURE_PORT = "securePort";
    protected static final String ELEM_COUNTRY_ID = "countryId";
    protected static final String ELEM_IDENTIFYING_ATTR = "identifyingAttribute";
    protected static final String ELEM_HEALTHCHECKURL = "healthCheckUrl";
    protected static final String ELEM_SECHEALTHCHECKURL = "secureHealthCheckUrl";
    protected static final String ELEM_APPGROUPNAME = "appGroupName";
    protected static final String ELEM_HOMEPAGEURL = "homePageUrl";
    protected static final String ELEM_STATUSPAGEURL = "statusPageUrl";
    protected static final String ELEM_VIPADDRESS = "vipAddress";
    protected static final String ELEM_SECVIPADDRESS = "secureVipAddress";
    protected static final String ELEM_ISCOORDINATINGDISCSOERVER = "isCoordinatingDiscoveryServer";
    protected static final String ELEM_LASTUPDATEDTS = "lastUpdatedTimestamp";
    protected static final String ELEM_LASTDIRTYTS = "lastDirtyTimestamp";
    protected static final String ELEM_ACTIONTYPE = "actionType";
    protected static final String ELEM_ASGNAME = "asgName";
    protected static final String ELEM_NAME = "name";
    protected static final String DATACENTER_METADATA = "metadata";

    protected static final String VERSIONS_DELTA_TEMPLATE = "versions_delta";
    protected static final String APPS_HASHCODE_TEMPTE = "apps_hashcode";

    public static EurekaJacksonCodec INSTANCE = new EurekaJacksonCodec();

    /**
     * XStream codec supports character replacement in field names to generate XML friendly
     * names. This feature is also configurable, and replacement strings can be provided by a user.
     * To obey these rules, version and appsHash key field names must be formatted according to the provided
     * configuration, which by default replaces '_' with '__' (double underscores).
     */
    private final String versionDeltaKey;
    private final String appHashCodeKey;

    private final ObjectMapper mapper;

    private final Map<Class<?>, Supplier<ObjectReader>> objectReaderByClass;
    private final Map<Class<?>, ObjectWriter> objectWriterByClass;

    static EurekaClientConfig loadConfig() {
        return com.netflix.discovery.DiscoveryManager.getInstance().getEurekaClientConfig();
    }
    
    public EurekaJacksonCodec() {
        this(formatKey(loadConfig(), VERSIONS_DELTA_TEMPLATE), formatKey(loadConfig(), APPS_HASHCODE_TEMPTE));
        
    }
    
    public EurekaJacksonCodec(String versionDeltaKey, String appsHashCodeKey) {
        this.versionDeltaKey = versionDeltaKey;
        this.appHashCodeKey = appsHashCodeKey;
        this.mapper = new ObjectMapper();
        this.mapper.setSerializationInclusion(Include.NON_NULL);

        SimpleModule module = new SimpleModule("eureka1.x", VERSION);
        module.addSerializer(DataCenterInfo.class, new DataCenterInfoSerializer());
        module.addSerializer(InstanceInfo.class, new InstanceInfoSerializer());
        module.addSerializer(Application.class, new ApplicationSerializer());
        module.addSerializer(Applications.class, new ApplicationsSerializer(this.versionDeltaKey, this.appHashCodeKey));

        module.addDeserializer(LeaseInfo.class, new LeaseInfoDeserializer());
        module.addDeserializer(InstanceInfo.class, new InstanceInfoDeserializer(this.mapper));
        module.addDeserializer(Application.class, new ApplicationDeserializer(this.mapper));
        module.addDeserializer(Applications.class, new ApplicationsDeserializer(this.mapper, this.versionDeltaKey, this.appHashCodeKey));
        this.mapper.registerModule(module);

        Map<Class<?>, Supplier<ObjectReader>> readers = new HashMap<>();
        readers.put(InstanceInfo.class, ()->mapper.reader().forType(InstanceInfo.class).withRootName("instance"));
        readers.put(Application.class, ()->mapper.reader().forType(Application.class).withRootName("application"));
        readers.put(Applications.class, ()->mapper.reader().forType(Applications.class).withRootName("applications"));
        this.objectReaderByClass = readers;

        Map<Class<?>, ObjectWriter> writers = new HashMap<>();
        writers.put(InstanceInfo.class, mapper.writer().forType(InstanceInfo.class).withRootName("instance"));
        writers.put(Application.class, mapper.writer().forType(Application.class).withRootName("application"));
        writers.put(Applications.class, mapper.writer().forType(Applications.class).withRootName("applications"));
        this.objectWriterByClass = writers;
    }

    protected ObjectMapper getMapper() {
        return mapper;
    }

    protected String getVersionDeltaKey() {
        return versionDeltaKey;
    }

    protected String getAppHashCodeKey() {
        return appHashCodeKey;
    }

    protected static String formatKey(EurekaClientConfig clientConfig, String keyTemplate) {
        String replacement;
        if (clientConfig == null) {
            replacement = "__";
        } else {
            replacement = clientConfig.getEscapeCharReplacement();
        }
        StringBuilder sb = new StringBuilder(keyTemplate.length() + 1);
        for (char c : keyTemplate.toCharArray()) {
            if (c == '_') {
                sb.append(replacement);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public <T> T readValue(Class<T> type, InputStream entityStream) throws IOException {
        ObjectReader reader = DeserializerStringCache.init(
                Optional.ofNullable(objectReaderByClass.get(type)).map(Supplier::get).orElseGet(()->mapper.readerFor(type))
                );
        try {
            return reader.readValue(entityStream);
        }
        finally {
            DeserializerStringCache.clear(reader, CacheScope.GLOBAL_SCOPE);
        }
    }

    public <T> T readValue(Class<T> type, String text) throws IOException {
        ObjectReader reader = DeserializerStringCache.init(
                Optional.ofNullable(objectReaderByClass.get(type)).map(Supplier::get).orElseGet(()->mapper.readerFor(type))
                );
        try {
            return reader.readValue(text);
        }
        finally {
            DeserializerStringCache.clear(reader, CacheScope.GLOBAL_SCOPE);
        }
    }

    public <T> void writeTo(T object, OutputStream entityStream) throws IOException {
        ObjectWriter writer = objectWriterByClass.get(object.getClass());
        if (writer == null) {
            mapper.writeValue(entityStream, object);
        } else {
            writer.writeValue(entityStream, object);
        }
    }

    public <T> String writeToString(T object) {
        try {
            ObjectWriter writer = objectWriterByClass.get(object.getClass());
            if (writer == null) {
                return mapper.writeValueAsString(object);
            }
            return writer.writeValueAsString(object);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot encode provided object", e);
        }
    }

    public static EurekaJacksonCodec getInstance() {
        return INSTANCE;
    }

    public static void setInstance(EurekaJacksonCodec instance) {
        INSTANCE = instance;
    }

    public static class DataCenterInfoSerializer extends JsonSerializer<DataCenterInfo> {
        @Override
        public void serializeWithType(DataCenterInfo dataCenterInfo, JsonGenerator jgen,
                                      SerializerProvider provider, TypeSerializer typeSer)
                throws IOException, JsonProcessingException {
            jgen.writeStartObject();

            // XStream encoded adds this for backwards compatibility issue. Not sure if needed anymore
            if (dataCenterInfo.getName() == Name.Amazon) {
                jgen.writeStringField("@class", "com.netflix.appinfo.AmazonInfo");
            } else {
                jgen.writeStringField("@class", "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo");
            }

            jgen.writeStringField(ELEM_NAME, dataCenterInfo.getName().name());

            if (dataCenterInfo.getName() == Name.Amazon) {
                AmazonInfo aInfo = (AmazonInfo) dataCenterInfo;
                jgen.writeObjectField(DATACENTER_METADATA, aInfo.getMetadata());
            }
            jgen.writeEndObject();
        }

        @Override
        public void serialize(DataCenterInfo dataCenterInfo, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            serializeWithType(dataCenterInfo, jgen, provider, null);
        }
    }

    public static class LeaseInfoDeserializer extends JsonDeserializer<LeaseInfo> {
        enum LeaseInfoField {
            DURATION("durationInSecs"),
            EVICTION_TIMESTAMP("evictionTimestamp"),
            LAST_RENEW_TIMESTAMP("lastRenewalTimestamp"),
            REG_TIMESTAMP("registrationTimestamp"),
            RENEW_INTERVAL("renewalIntervalInSecs"),
            SERVICE_UP_TIMESTAMP("serviceUpTimestamp")
            ;
            private final char[] fieldName;
            private LeaseInfoField(String fieldName) {
                this.fieldName = fieldName.toCharArray();
            }
            public char[] getFieldName() {
                return fieldName;
            }
        }
        private static EnumLookup<LeaseInfoField> fieldLookup = new EnumLookup<>(LeaseInfoField.class, LeaseInfoField::getFieldName);

        @Override
        public LeaseInfo deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            LeaseInfo.Builder builder = LeaseInfo.Builder.newBuilder();
            JsonToken jsonToken;
            while ((jsonToken = jp.nextToken()) != JsonToken.END_OBJECT) {
                LeaseInfoField field = fieldLookup.find(jp);
                jsonToken = jp.nextToken();
                if (field != null && jsonToken != JsonToken.VALUE_NULL) {
                    switch(field) {
                    case DURATION: 
                        builder.setDurationInSecs(jp.getValueAsInt());
                        break;
                    case EVICTION_TIMESTAMP:
                        builder.setEvictionTimestamp(jp.getValueAsLong());
                        break;
                    case LAST_RENEW_TIMESTAMP:
                        builder.setRenewalTimestamp(jp.getValueAsLong());
                        break;
                    case REG_TIMESTAMP:
                        builder.setRegistrationTimestamp(jp.getValueAsLong());
                        break;
                    case RENEW_INTERVAL:
                        builder.setRenewalIntervalInSecs(jp.getValueAsInt());
                        break;
                    case SERVICE_UP_TIMESTAMP: 
                        builder.setServiceUpTimestamp(jp.getValueAsLong());
                        break;
                    }
                }
            }
            return builder.build();
        }
    }

    public static class InstanceInfoSerializer extends JsonSerializer<InstanceInfo> {
        // For backwards compatibility
        public static final String METADATA_COMPATIBILITY_KEY = "@class";
        public static final String METADATA_COMPATIBILITY_VALUE = "java.util.Collections$EmptyMap";
        protected static final Object EMPTY_METADATA = Collections.singletonMap(METADATA_COMPATIBILITY_KEY, METADATA_COMPATIBILITY_VALUE);

        @Override
        public void serialize(InstanceInfo info, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeStartObject();

            if (info.getInstanceId() != null) {
                jgen.writeStringField(ELEM_INSTANCE_ID, info.getInstanceId());
            }
            jgen.writeStringField(ELEM_HOST, info.getHostName());
            jgen.writeStringField(ELEM_APP, info.getAppName());
            jgen.writeStringField(ELEM_IP, info.getIPAddr());

            @SuppressWarnings("deprecation")
            String sid = info.getSID();
            if (!("unknown".equals(sid) || "na".equals(sid))) {
                jgen.writeStringField(ELEM_SID, sid);
            }

            jgen.writeStringField(ELEM_STATUS, info.getStatus().name());
            jgen.writeStringField(ELEM_OVERRIDDEN_STATUS, info.getOverriddenStatus().name());

            jgen.writeFieldName(ELEM_PORT);
            jgen.writeStartObject();
            jgen.writeNumberField("$", info.getPort());
            jgen.writeStringField("@enabled", Boolean.toString(info.isPortEnabled(PortType.UNSECURE)));
            jgen.writeEndObject();

            jgen.writeFieldName(ELEM_SECURE_PORT);
            jgen.writeStartObject();
            jgen.writeNumberField("$", info.getSecurePort());
            jgen.writeStringField("@enabled", Boolean.toString(info.isPortEnabled(PortType.SECURE)));
            jgen.writeEndObject();

            jgen.writeNumberField(ELEM_COUNTRY_ID, info.getCountryId());

            if (info.getDataCenterInfo() != null) {
                jgen.writeObjectField(NODE_DATACENTER, info.getDataCenterInfo());
            }
            if (info.getLeaseInfo() != null) {
                jgen.writeObjectField(NODE_LEASE, info.getLeaseInfo());
            }

            Map<String, String> metadata = info.getMetadata();
            if (metadata != null) {
                if (metadata.isEmpty()) {
                    jgen.writeObjectField(NODE_METADATA, EMPTY_METADATA);
                } else {
                    jgen.writeObjectField(NODE_METADATA, metadata);
                }
            }
            autoMarshalEligible(info, jgen);

            jgen.writeEndObject();
        }

        protected void autoMarshalEligible(Object o, JsonGenerator jgen) {
            try {
                Class<?> c = o.getClass();
                Field[] fields = c.getDeclaredFields();
                Annotation annotation;
                for (Field f : fields) {
                    annotation = f.getAnnotation(Auto.class);
                    if (annotation != null) {
                        f.setAccessible(true);
                        if (f.get(o) != null) {
                            jgen.writeStringField(f.getName(), String.valueOf(f.get(o)));
                        }

                    }
                }
            } catch (Throwable th) {
                logger.error("Error in marshalling the object", th);
            }
        }
    }
    
    public static class InstanceInfoDeserializer extends JsonDeserializer<InstanceInfo> {
        private static char[] BUF_AT_CLASS = "@class".toCharArray();

        enum InstanceInfoField {
            HOSTNAME(ELEM_HOST),
            INSTANCE_ID(ELEM_INSTANCE_ID),
            APP(ELEM_APP),                    
            IP(ELEM_IP),
            SID(ELEM_SID),
            ID_ATTR(ELEM_IDENTIFYING_ATTR),// nothing 
            STATUS(ELEM_STATUS),
            OVERRIDDEN_STATUS(ELEM_OVERRIDDEN_STATUS),
            PORT(ELEM_PORT),
            SECURE_PORT(ELEM_SECURE_PORT),
            COUNTRY_ID(ELEM_COUNTRY_ID),
            DATACENTER(NODE_DATACENTER),
            LEASE(NODE_LEASE),
            HEALTHCHECKURL(ELEM_HEALTHCHECKURL),
            SECHEALTHCHECKURL(ELEM_SECHEALTHCHECKURL),
            APPGROUPNAME(ELEM_APPGROUPNAME),
            HOMEPAGEURL(ELEM_HOMEPAGEURL),
            STATUSPAGEURL(ELEM_STATUSPAGEURL),
            VIPADDRESS(ELEM_VIPADDRESS),
            SECVIPADDRESS(ELEM_SECVIPADDRESS),
            ISCOORDINATINGDISCSERVER(ELEM_ISCOORDINATINGDISCSOERVER),
            LASTUPDATEDTS(ELEM_LASTUPDATEDTS),
            LASTDIRTYTS(ELEM_LASTDIRTYTS),
            ACTIONTYPE(ELEM_ACTIONTYPE),
            ASGNAME(ELEM_ASGNAME),
            METADATA(NODE_METADATA)
            ;
            private final char[] elementName;
            private InstanceInfoField(String elementName) {
                this.elementName = elementName.toCharArray();
            }
            public char[] getElementName() {
                return elementName;
            }
            public static EnumLookup<InstanceInfoField> lookup = new EnumLookup<>(InstanceInfoField.class, InstanceInfoField::getElementName);
        }
        
        enum PortField {
            PORT("$"), ENABLED("@enabled");
            private final char[] fieldName;
            private PortField(String name) {
                this.fieldName = name.toCharArray();
            }
            public char[] getFieldName() { return fieldName; }
            public static EnumLookup<PortField> lookup = new EnumLookup<>(PortField.class, PortField::getFieldName);
        }

        private final ObjectMapper mapper;
        private final ConcurrentMap<String, BiConsumer<Object, String>> autoUnmarshalActions = new ConcurrentHashMap<>();
        private static EnumLookup<InstanceStatus> statusLookup = new EnumLookup<>(InstanceStatus.class);
        private static EnumLookup<ActionType> actionTypeLookup = new EnumLookup<>(ActionType.class);
        static Set<String> globalCachedMetadata = new HashSet<>();
        static {
            globalCachedMetadata.add("route53Type");
            globalCachedMetadata.add("enableRoute53");
            globalCachedMetadata.add("netflix.stack");
            globalCachedMetadata.add("netflix.detail");
            globalCachedMetadata.add("NETFLIX_ENVIRONMENT");
            globalCachedMetadata.add("transportPort");
        }


        protected InstanceInfoDeserializer(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        final static Function<String,String> self = s->s;
        @SuppressWarnings("deprecation")
        @Override
        public InstanceInfo deserialize(JsonParser jp, DeserializationContext context) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                throw new JsonParseException(jp, "processing aborted");
            }
            DeserializerStringCache intern = DeserializerStringCache.from(context);
            InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder(self);
            JsonToken jsonToken;
            while ((jsonToken = jp.nextToken()) != JsonToken.END_OBJECT) {
                InstanceInfoField instanceInfoField = InstanceInfoField.lookup.find(jp);
                jsonToken = jp.nextToken();
                if (instanceInfoField != null && jsonToken != JsonToken.VALUE_NULL) {
                    switch(instanceInfoField) {
                    case HOSTNAME:
                        builder.setHostName(intern.apply(jp));
                        break;
                    case INSTANCE_ID:
                        builder.setInstanceId(intern.apply(jp));
                        break;
                    case APP:
                        builder.setAppNameForDeser(
                                intern.apply(jp, CacheScope.APPLICATION_SCOPE,
                                ()->{
                                    try {
                                        return jp.getText().toUpperCase();
                                    } catch (IOException e) {
                                        throw new RuntimeJsonMappingException(e.getMessage());
                                    }
                              }));
                        break;
                    case IP:
                        builder.setIPAddr(intern.apply(jp));
                        break;
                    case SID:
                        builder.setSID(intern.apply(jp, CacheScope.GLOBAL_SCOPE));
                        break;
                    case ID_ATTR:
                        // nothing
                        break;
                    case STATUS:
                        builder.setStatus(statusLookup.find(jp, InstanceStatus.UNKNOWN));
                        break;
                    case OVERRIDDEN_STATUS:
                        builder.setOverriddenStatus(statusLookup.find(jp, InstanceStatus.UNKNOWN));
                        break;
                    case PORT:
                        while ((jsonToken = jp.nextToken()) != JsonToken.END_OBJECT) {
                            PortField field = PortField.lookup.find(jp);
                            switch(field) {
                            case PORT:
                                if (jsonToken == JsonToken.FIELD_NAME) jp.nextToken();
                                builder.setPort(jp.getValueAsInt());
                                break;
                            case ENABLED:                            
                                if (jsonToken == JsonToken.FIELD_NAME) jp.nextToken();
                                builder.enablePort(PortType.UNSECURE, jp.getValueAsBoolean());
                                break;
                            default:
                            }
                        }
                        break;
                    case SECURE_PORT:
                        while ((jsonToken = jp.nextToken()) != JsonToken.END_OBJECT) {
                            PortField field = PortField.lookup.find(jp);
                            switch(field) {
                            case PORT:
                                if (jsonToken == JsonToken.FIELD_NAME) jp.nextToken();
                                builder.setSecurePort(jp.getValueAsInt());
                                break;
                            case ENABLED:                            
                                if (jsonToken == JsonToken.FIELD_NAME) jp.nextToken();
                                builder.enablePort(PortType.SECURE, jp.getValueAsBoolean());
                                break;
                            default:
                            }
                        }
                        break;
                    case COUNTRY_ID:
                        builder.setCountryId(jp.getValueAsInt());
                        break;
                    case DATACENTER:
                        builder.setDataCenterInfo(DeserializerStringCache.init(mapper.readerFor(DataCenterInfo.class), context).readValue(jp));
                        break;
                    case LEASE:
                        builder.setLeaseInfo(mapper.readerFor(LeaseInfo.class).readValue(jp));
                        break;
                    case HEALTHCHECKURL:
                        builder.setHealthCheckUrlsForDeser(intern.apply(jp.getText()), null);
                        break;
                    case SECHEALTHCHECKURL:
                        builder.setHealthCheckUrlsForDeser(null, intern.apply(jp.getText()));
                        break;
                    case APPGROUPNAME:
                        builder.setAppGroupNameForDeser(intern.apply(jp, CacheScope.GLOBAL_SCOPE, 
                                ()->{
                                    try {
                                        return jp.getText().toUpperCase();
                                    } catch (IOException e) {
                                        throw new RuntimeJsonMappingException(e.getMessage());
                                    }
                              }));
                        break;
                    case HOMEPAGEURL:
                        builder.setHomePageUrlForDeser(intern.apply(jp.getText()));
                        break;
                    case STATUSPAGEURL:
                        builder.setStatusPageUrlForDeser(intern.apply(jp.getText()));
                        break;
                    case VIPADDRESS:
                        builder.setVIPAddressDeser(intern.apply(jp));
                        break;
                    case SECVIPADDRESS:
                        builder.setSecureVIPAddressDeser(intern.apply(jp));
                        break;
                    case ISCOORDINATINGDISCSERVER:
                        builder.setIsCoordinatingDiscoveryServer(jp.getValueAsBoolean());
                        break;
                    case LASTUPDATEDTS:
                        builder.setLastUpdatedTimestamp(jp.getValueAsLong());
                        break;
                    case LASTDIRTYTS:
                        builder.setLastDirtyTimestamp(jp.getValueAsLong());
                        break;
                    case ACTIONTYPE:
                        builder.setActionType(actionTypeLookup.find(jp.getTextCharacters(), jp.getTextOffset(), jp.getTextLength()));
                        break;
                    case ASGNAME:
                        builder.setASGName(intern.apply(jp));
                        break;
                    case METADATA:
                        Map<String, String> metadataMap = null;
                        while ((jsonToken = jp.nextToken()) != JsonToken.END_OBJECT) {
                            char[] parserChars = jp.getTextCharacters();
                            if (parserChars[0] == '@' && EnumLookup.equals(BUF_AT_CLASS, parserChars, jp.getTextOffset(), jp.getTextLength())) {
                                // skip this
                                jsonToken = jp.nextToken();
                            }
                            else { // For backwards compatibility
                                String key = intern.apply(jp, CacheScope.GLOBAL_SCOPE);
                                jsonToken = jp.nextToken();
                                String value = intern.apply(jp, CacheScope.APPLICATION_SCOPE );
                                metadataMap = Optional.ofNullable(metadataMap).orElseGet(CompactHashMap::new);
                                metadataMap.put(key, value);
                            }
                        };   
                        builder.setMetadata(metadataMap == null ? Collections.emptyMap() : Collections.synchronizedMap(metadataMap));
                        break;
                    default:                    
                        autoUnmarshalEligible(jp.getCurrentName(), jp.getValueAsString(), builder.getRawInstance());
                    }
                }
                else {
                    autoUnmarshalEligible(jp.getCurrentName(), jp.getValueAsString(), builder.getRawInstance());
                }
            }
            return builder.build();
        }

        void autoUnmarshalEligible(String fieldName, String value, Object o) {
            if (value == null || o == null) return; // early out
            Class<?> c = o.getClass();
            String cacheKey = c.getName() + ":" + fieldName;
            BiConsumer<Object, String> action = autoUnmarshalActions.computeIfAbsent(cacheKey, k-> {
                try {
                    Field f = null;
                    try {
                        f = c.getDeclaredField(fieldName);
                    } catch (NoSuchFieldException e) {
                        // TODO XStream version increments metrics counter here
                    }
                    if (f == null) {
                        return (t,v)->{};
                    }
                    Annotation annotation = f.getAnnotation(Auto.class);
                    if (annotation == null) {
                        return (t,v)->{};
                    }
                    f.setAccessible(true);

                    final Field setterField = f;
                    Class<?> returnClass = setterField.getType();
                    if (!String.class.equals(returnClass)) {
                        Method method = returnClass.getDeclaredMethod("valueOf", java.lang.String.class);
                        return (t, v) -> tryCatchLog(()->{ setterField.set(t, method.invoke(returnClass, v)); return null; });
                    } else {
                        return (t, v) -> tryCatchLog(()->{ setterField.set(t, v); return null; });
                    }  
                } catch (Exception ex) {
                    logger.error("Error in unmarshalling the object:", ex);
                    return null;
                }
            });
            action.accept(o, value);
        }
    }

    private static void tryCatchLog(Callable<Void> callable) {
        try {    
            callable.call();
        } catch (Exception ex) {
            logger.error("Error in unmarshalling the object:", ex);
        }
    }

    public static class ApplicationSerializer extends JsonSerializer<Application> {
        @Override
        public void serialize(Application value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
            jgen.writeStartObject();
            jgen.writeStringField(ELEM_NAME, value.getName());
            jgen.writeObjectField(ELEM_INSTANCE, value.getInstances());
            jgen.writeEndObject();
        }
    }

    public static class ApplicationDeserializer extends JsonDeserializer<Application> {
        enum ApplicationField {
            NAME(ELEM_NAME), INSTANCE(ELEM_INSTANCE);
            private final char[] fieldName;
            private ApplicationField(String name) {
                this.fieldName = name.toCharArray();
            }
            public char[] getFieldName() { return fieldName; }
            public static EnumLookup<ApplicationField> lookup = new EnumLookup<>(ApplicationField.class, ApplicationField::getFieldName);
            
        }

        private final ObjectMapper mapper;

        public ApplicationDeserializer(ObjectMapper mapper) {
            this.mapper = mapper;
        }
        
        @Override
        public Application deserialize(JsonParser jp, DeserializationContext context) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                throw new JsonParseException(jp, "processing aborted");
            }
            Application application = new Application();
            JsonToken jsonToken;
            try {
                while((jsonToken = jp.nextToken()) != JsonToken.END_OBJECT){
                    if(JsonToken.FIELD_NAME == jsonToken){
                        ApplicationField field = ApplicationField.lookup.find(jp);
                        jsonToken = jp.nextToken();
                        if (field != null) {
                            switch(field) {
                            case NAME:
                                application.setName(jp.getText());
                                break;
                            case INSTANCE:
                                ObjectReader instanceInfoReader = DeserializerStringCache.init(mapper.readerFor(InstanceInfo.class), context);
                                if (jsonToken == JsonToken.START_ARRAY) {
                                    // messages is array, loop until token equal to "]"
                                    while (jp.nextToken() != JsonToken.END_ARRAY) {
                                        application.addInstance(instanceInfoReader.readValue(jp));
                                    }
                                }
                                else if (jsonToken == JsonToken.START_OBJECT) {
                                    application.addInstance(instanceInfoReader.readValue(jp));
                                }
                                break;
                            }
                         }
                    }
                }
            }
            finally {
//                DeserializerStringCache.clear(context, CacheScope.APPLICATION_SCOPE);
            }
            return application;
        }
    }

    public static class ApplicationsSerializer extends JsonSerializer<Applications> {
        protected String versionDeltaKey;
        protected String appHashCodeKey;

        public ApplicationsSerializer(String versionDeltaKey, String appHashCodeKey) {
            this.versionDeltaKey = versionDeltaKey;
            this.appHashCodeKey = appHashCodeKey;
        }

        @Override
        public void serialize(Applications applications, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            jgen.writeStartObject();
            jgen.writeStringField(versionDeltaKey, applications.getVersion().toString());
            jgen.writeStringField(appHashCodeKey, applications.getAppsHashCode());
            jgen.writeObjectField(NODE_APP, applications.getRegisteredApplications());
        }
    }

    public static class ApplicationsDeserializer extends JsonDeserializer<Applications> {
        protected ObjectMapper mapper;
        protected String versionDeltaKey;
        protected String appHashCodeKey;

        public ApplicationsDeserializer(ObjectMapper mapper, String versionDeltaKey, String appHashCodeKey) {
            this.mapper = mapper;
            this.versionDeltaKey = versionDeltaKey;
            this.appHashCodeKey = appHashCodeKey;
        }

        @Override
        public Applications deserialize(JsonParser jp, DeserializationContext context) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                throw new JsonParseException(jp, "processing aborted");
            }
            Applications apps = new Applications();
            JsonToken jsonToken;
            while((jsonToken = jp.nextToken()) != JsonToken.END_OBJECT){
                
                if(JsonToken.FIELD_NAME == jsonToken){
                    String fieldName = jp.getCurrentName();
                    jsonToken = jp.nextToken();

                    if(versionDeltaKey.equals(fieldName)){
                        apps.setVersion(jp.getValueAsLong());
                    } else if (appHashCodeKey.equals(fieldName)){
                        apps.setAppsHashCode(jp.getValueAsString());
                    }
                    else if (NODE_APP.equals(fieldName)) {
                        ObjectReader applicationReader = DeserializerStringCache.init(mapper.readerFor(Application.class), context);
                        if (jsonToken == JsonToken.START_ARRAY) {
                            while (jp.nextToken() != JsonToken.END_ARRAY) {
                                apps.addApplication(applicationReader.readValue(jp));
                            }                            
                        }
                        else if (jsonToken == JsonToken.START_OBJECT) {
                            apps.addApplication(applicationReader.readValue(jp));
                        }
                    }
                }
            }
            return apps;
        }
    }

}
