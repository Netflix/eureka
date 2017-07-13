package com.netflix.discovery.converters;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
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
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.DeserializerStringCache;

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
     * To obey these rules, version and apppsHash key field names must be formatted according to the provided
     * configuration, which by default replaces '_' with '__' (double underscores).
     */
    private final String versionDeltaKey;
    private final String appHashCodeKey;

    private final ObjectMapper mapper;

    private final Map<Class<?>, Supplier<ObjectReader>> objectReaderByClass;
    private final Map<Class<?>, ObjectWriter> objectWriterByClass;

    public EurekaJacksonCodec() {
        this.versionDeltaKey = formatKey(VERSIONS_DELTA_TEMPLATE);
        this.appHashCodeKey = formatKey(APPS_HASHCODE_TEMPTE);
        this.mapper = new ObjectMapper();

        this.mapper.setSerializationInclusion(Include.NON_NULL);

        SimpleModule module = new SimpleModule("eureka1.x", VERSION);
        module.addSerializer(DataCenterInfo.class, new DataCenterInfoSerializer());
        module.addSerializer(InstanceInfo.class, new InstanceInfoSerializer());
        module.addSerializer(Application.class, new ApplicationSerializer());
        module.addSerializer(Applications.class, new ApplicationsSerializer(this.versionDeltaKey, this.appHashCodeKey));

        module.addDeserializer(DataCenterInfo.class, new DataCenterInfoDeserializer());
        module.addDeserializer(LeaseInfo.class, new LeaseInfoDeserializer());
        module.addDeserializer(InstanceInfo.class, new InstanceInfoDeserializer(this.mapper));
        module.addDeserializer(Application.class, new ApplicationDeserializer(this.mapper));
        module.addDeserializer(Applications.class, new ApplicationsDeserializer(this.mapper, this.versionDeltaKey, this.appHashCodeKey));
        this.mapper.registerModule(module);

        HashMap<Class<?>, Supplier<ObjectReader>> readers = new HashMap<>();
        readers.put(InstanceInfo.class, ()->mapper.reader().forType(InstanceInfo.class).withRootName("instance"));
        readers.put(Application.class, ()->mapper.reader().forType(Application.class).withRootName("application"));
        readers.put(Applications.class, ()->mapper.reader().forType(Applications.class).withRootName("applications"));
        this.objectReaderByClass = readers;

        HashMap<Class<?>, ObjectWriter> writers = new HashMap<>();
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

    protected static String formatKey(String keyTemplate) {
        EurekaClientConfig clientConfig = DiscoveryManager.getInstance().getEurekaClientConfig();
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
            DeserializerStringCache.clear(reader);
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
            DeserializerStringCache.clear(reader);
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

    public static class DataCenterInfoDeserializer extends JsonDeserializer<DataCenterInfo> {

        @Override
        public DataCenterInfo deserialize(JsonParser jp, DeserializationContext context) throws IOException {
            JsonNode node = jp.getCodec().readTree(jp);
            final Name name = Name.valueOf(node.get(ELEM_NAME).asText());
            if (name != Name.Amazon) {
                return new DataCenterInfo() {
                    @Override
                    public Name getName() {
                        return name;
                    }
                };
            }
            

            Function<String,String> intern = DeserializerStringCache.from(context);
            Map<String, String> metaData = new HashMap<String, String>();
            JsonNode metaNode = node.get(DATACENTER_METADATA);
            metaNode.fieldNames().forEachRemaining(fieldName->{
                metaData.put(intern.apply(fieldName), intern.apply(metaNode.get(fieldName).asText()));
            });
            AmazonInfo amazonInfo = new AmazonInfo();
            amazonInfo.setMetadata(metaData); 

            return amazonInfo;
        }
    }

    public static class LeaseInfoDeserializer extends JsonDeserializer<LeaseInfo> {

        protected static final String ELEM_RENEW_INT = "renewalIntervalInSecs";
        protected static final String ELEM_DURATION = "durationInSecs";
        protected static final String ELEM_REG_TIMESTAMP = "registrationTimestamp";
        protected static final String ELEM_LAST_RENEW_TIMESTAMP = "lastRenewalTimestamp";
        protected static final String ELEM_EVICTION_TIMESTAMP = "evictionTimestamp";
        protected static final String ELEM_SERVICE_UP_TIMESTAMP = "serviceUpTimestamp";
        private static Map<String, BiConsumer<LeaseInfo.Builder, JsonNode>> mappingActions = new HashMap<>();        
        static {
            mappingActions.put(ELEM_DURATION, (builder,node)->builder.setDurationInSecs(node.asInt()));
            mappingActions.put(ELEM_EVICTION_TIMESTAMP, (builder,node)->builder.setEvictionTimestamp(node.asLong()));
            mappingActions.put(ELEM_LAST_RENEW_TIMESTAMP, (builder,node)->builder.setRenewalTimestamp(node.asLong()));
            mappingActions.put(ELEM_REG_TIMESTAMP, (builder,node)->builder.setRegistrationTimestamp(node.asLong()));
            mappingActions.put(ELEM_RENEW_INT, (builder,node)->builder.setRenewalIntervalInSecs(node.asInt()));
            mappingActions.put(ELEM_SERVICE_UP_TIMESTAMP, (builder,node)->builder.setServiceUpTimestamp(node.asLong()));
        }

        @Override
        public LeaseInfo deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            LeaseInfo.Builder builder = LeaseInfo.Builder.newBuilder();

            JsonNode node = jp.getCodec().readTree(jp);
            node.fieldNames().forEachRemaining( fieldName -> {
                if (node.has(fieldName)) {
                    Optional.ofNullable(mappingActions.get(fieldName)).ifPresent(f->f.accept(builder, node.get(fieldName)));
                }
            }    );           
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

            if (!("unknown".equals(info.getSID()) || "na".equals(info.getSID()))) {
                jgen.writeStringField(ELEM_SID, info.getSID());
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
                Class c = o.getClass();
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
        static class RuntimeJsonProcessingException extends RuntimeException {
            private static final long serialVersionUID = 1L;
            RuntimeJsonProcessingException(JsonProcessingException jpe) {
                super(jpe);
            }
        }
        
        protected ObjectMapper mapper;
        private ConcurrentMap<String, BiConsumer<Object, String>> autoUnmarshalActions = new ConcurrentHashMap<>();
        private Map<String, BiConsumer<InstanceInfo.Builder, JsonNode>> mapping = new HashMap<>();        
        {
            mapping.put(ELEM_HOST, (builder,node)->builder.setHostName(node.asText()));
            mapping.put(ELEM_INSTANCE_ID, (builder,node)->builder.setInstanceId(node.asText()));
            mapping.put(ELEM_APP, (builder,node)->builder.setAppName(node.asText()));
            mapping.put(ELEM_IP, (builder,node)->builder.setIPAddr(node.asText()));
            mapping.put(ELEM_SID, (builder,node)->builder.setSID(node.asText()));
            mapping.put(ELEM_IDENTIFYING_ATTR, (builder,node)->{});// nothing 
            mapping.put(ELEM_STATUS, (builder,node)->builder.setStatus(InstanceStatus.toEnum(node.asText())));
            mapping.put(ELEM_OVERRIDDEN_STATUS, (builder,node)-> builder.setOverriddenStatus(InstanceStatus.toEnum(node.asText())));
            mapping.put(ELEM_PORT, (builder,node)->{
                int port = node.get("$").asInt();
                boolean enabled = node.get("@enabled").asBoolean();
                builder.setPort(port);
                builder.enablePort(PortType.UNSECURE, enabled); 
            });
            mapping.put(ELEM_SECURE_PORT, (builder,node)->{
                int port = node.get("$").asInt();
                boolean enabled = node.get("@enabled").asBoolean();
                builder.setSecurePort(port);
                builder.enablePort(PortType.SECURE, enabled);                
            });
            mapping.put(ELEM_COUNTRY_ID, (builder,node)->builder.setCountryId(node.asInt()));
            mapping.put(NODE_DATACENTER, (builder,node)->{
                try {
                    builder.setDataCenterInfo(mapper.treeToValue(node, DataCenterInfo.class)); 
                }
                catch (JsonProcessingException jpe) {
                    throw new RuntimeJsonProcessingException(jpe);
                }
            });
            mapping.put(NODE_LEASE, (builder,node)->{
                try {
                    builder.setLeaseInfo(mapper.treeToValue(node, LeaseInfo.class));
                }
                catch (JsonProcessingException jpe) {
                    throw new RuntimeJsonProcessingException(jpe);
                }
            });
            mapping.put(ELEM_HEALTHCHECKURL, (builder,node)->builder.setHealthCheckUrlsForDeser(node.asText(), null));
            mapping.put(ELEM_SECHEALTHCHECKURL, (builder,node)->builder.setHealthCheckUrlsForDeser(null, node.asText()));
            mapping.put(ELEM_APPGROUPNAME, (builder,node)-> builder.setAppGroupName(node.asText()));
            mapping.put(ELEM_HOMEPAGEURL, (builder,node)->builder.setHomePageUrlForDeser(node.asText()));
            mapping.put(ELEM_STATUSPAGEURL, (builder,node)->builder.setStatusPageUrlForDeser(node.asText()));
            mapping.put(ELEM_VIPADDRESS, (builder,node)->builder.setVIPAddressDeser(node.asText()));
            mapping.put(ELEM_SECVIPADDRESS, (builder,node)->builder.setSecureVIPAddressDeser(node.asText()));
            mapping.put(ELEM_ISCOORDINATINGDISCSOERVER, (builder,node)->builder.setIsCoordinatingDiscoveryServer(node.asBoolean()));
            mapping.put(ELEM_LASTUPDATEDTS, (builder,node)->builder.setLastUpdatedTimestamp(node.asLong()));
            mapping.put(ELEM_LASTDIRTYTS, (builder,node)->builder.setLastDirtyTimestamp(node.asLong()));
            mapping.put(ELEM_ACTIONTYPE, (builder,node)->builder.setActionType(ActionType.valueOf(node.asText())));
            mapping.put(ELEM_ASGNAME, (builder,node)->builder.setASGName(node.asText()));
        }

        protected InstanceInfoDeserializer(ObjectMapper mapper) {
            this.mapper = mapper;
        }
        
        @Override
        public InstanceInfo deserialize(JsonParser jp, DeserializationContext context) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                throw new JsonParseException(jp, "processing aborted");
            }
            Function<String,String> intern = DeserializerStringCache.from(context);
            InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder(intern);
            JsonNode node = jp.getCodec().readTree(jp);
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if (node.has(fieldName)) {
                    BiConsumer<InstanceInfo.Builder, JsonNode> consumer = mapping.getOrDefault(
                            fieldName, 
                            (b, n) -> {
                                if (NODE_METADATA.equals(fieldName)) {
                                    b.setMetadata(unmarshalMetadata(n, intern));
                                }
                                else {
                                    autoUnmarshalEligible(fieldName, n.asText(), b.getRawInstance());
                                }
                            }
                    );
                    try {
                        consumer.accept(builder, node.get(fieldName));
                    }
                    catch (RuntimeJsonProcessingException rjpe) {
                        throw (JsonProcessingException)rjpe.getCause();
                    }
                }
            }
            return builder.build();
        }
        
        protected Map<String,String> unmarshalMetadata(JsonNode node, Function<String,String> intern) {
            Map<String, String> meta = null;
            Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String fieldName = fields.next();
                if (!"@class".equals(fieldName)) { // For backwards compatibility
                    String key = intern.apply(fieldName);
                    String value = intern.apply(node.get(fieldName).asText());
                    if (meta == null) {
                        meta = new ConcurrentHashMap<String, String>();
                    }
                    meta.put(key, value);
                }
            };
            return (meta == null) ? Collections.emptyMap() : meta;         
        }

        protected void autoUnmarshalEligible(String fieldName, String value, Object o) {
            if (value == null) return; // early out
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
                } catch (Throwable th) {
                    logger.error("Error in unmarshalling the object:", th);   
                    return null;
                }                
            });
            action.accept(o, value);
        }
    }
    
    private static void tryCatchLog(Callable<Void> callable) {
        try {    
            callable.call();
        } catch (Throwable th) {
            logger.error("Error in unmarshalling the object:", th);
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

        protected ObjectMapper mapper;

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
            while((jsonToken = jp.nextToken()) != JsonToken.END_OBJECT){                
                if(JsonToken.FIELD_NAME == jsonToken){
                    String fieldName = jp.getCurrentName();
                    jsonToken = jp.nextToken();

                    if(ELEM_NAME.equals(fieldName)){
                        application.setName(jp.getValueAsString());
                    }
                    else if (ELEM_INSTANCE.equals(fieldName)) {
                        ObjectReader instanceInfoReader = DeserializerStringCache.init(mapper.readerFor(InstanceInfo.class));
                        if (jsonToken == JsonToken.START_ARRAY) {
                            // messages is array, loop until token equal to "]"
                            while (jp.nextToken() != JsonToken.END_ARRAY) {
                                application.addInstance(instanceInfoReader.readValue(jp));
                            }                            
                        }
                        else if (jsonToken == JsonToken.START_OBJECT) {
                            application.addInstance(instanceInfoReader.readValue(jp));
                        }
                    }
                }
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
                        ObjectReader applicationReader = DeserializerStringCache.init(mapper.readerFor(Application.class));
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
            DeserializerStringCache.clear(context);
            return apps;
        }
    }
}
