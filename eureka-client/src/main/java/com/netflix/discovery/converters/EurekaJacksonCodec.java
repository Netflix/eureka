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
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import com.netflix.discovery.util.StringCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    protected static final String ELEM_OVERRIDDEN_STATUS = "overriddenstatus";
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

    private final Map<Class<?>, ObjectReader> objectReaderByClass;
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

        HashMap<Class<?>, ObjectReader> readers = new HashMap<>();
        readers.put(InstanceInfo.class, mapper.reader().withType(InstanceInfo.class).withRootName("instance"));
        readers.put(Application.class, mapper.reader().withType(Application.class).withRootName("application"));
        readers.put(Applications.class, mapper.reader().withType(Applications.class).withRootName("applications"));
        this.objectReaderByClass = readers;

        HashMap<Class<?>, ObjectWriter> writers = new HashMap<>();
        writers.put(InstanceInfo.class, mapper.writer().withType(InstanceInfo.class).withRootName("instance"));
        writers.put(Application.class, mapper.writer().withType(Application.class).withRootName("application"));
        writers.put(Applications.class, mapper.writer().withType(Applications.class).withRootName("applications"));
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
        ObjectReader reader = objectReaderByClass.get(type);
        if (reader == null) {
            return mapper.readValue(entityStream, type);
        }
        return reader.readValue(entityStream);
    }

    public <T> T readValue(Class<T> type, String text) throws IOException {
        ObjectReader reader = objectReaderByClass.get(type);
        if (reader == null) {
            return mapper.readValue(text, type);
        }
        return reader.readValue(text);
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

            Map<String, String> metaData = new HashMap<String, String>();
            JsonNode metaNode = node.get(DATACENTER_METADATA);
            Iterator<String> metaNamesIt = metaNode.fieldNames();
            while (metaNamesIt.hasNext()) {
                String key = metaNamesIt.next();
                String value = metaNode.get(key).asText();
                metaData.put(StringCache.intern(key), StringCache.intern(value));
            }

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

        @Override
        public LeaseInfo deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            LeaseInfo.Builder builder = LeaseInfo.Builder.newBuilder();

            JsonNode node = jp.getCodec().readTree(jp);
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String nodeName = fieldNames.next();
                if (!node.get(nodeName).isNull()) {
                    long longValue = node.get(nodeName).asLong();
                    if (ELEM_DURATION.equals(nodeName)) {
                        builder.setDurationInSecs((int) longValue);
                    } else if (ELEM_EVICTION_TIMESTAMP.equals(nodeName)) {
                        builder.setEvictionTimestamp(longValue);
                    } else if (ELEM_LAST_RENEW_TIMESTAMP.equals(nodeName)) {
                        builder.setRenewalTimestamp(longValue);
                    } else if (ELEM_REG_TIMESTAMP.equals(nodeName)) {
                        builder.setRegistrationTimestamp(longValue);
                    } else if (ELEM_RENEW_INT.equals(nodeName)) {
                        builder.setRenewalIntervalInSecs((int) longValue);
                    } else if (ELEM_SERVICE_UP_TIMESTAMP.equals(nodeName)) {
                        builder.setServiceUpTimestamp(longValue);
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

            if (!("unknown".equals(info.getSID()) || "na".equals(info.getSID()))) {
                jgen.writeStringField(ELEM_SID, info.getSID());
            }

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
            jgen.writeStringField(ELEM_STATUS, info.getStatus().name());

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
        protected ObjectMapper mapper;

        protected InstanceInfoDeserializer(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public InstanceInfo deserialize(JsonParser jp, DeserializationContext context) throws IOException {
            InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder();

            JsonNode node = jp.getCodec().readTree(jp);

            /**
             * These are set via single call to
             * {@link com.netflix.appinfo.InstanceInfo.Builder#setHealthCheckUrlsForDeser(String, String, String)}.
             */
            String healthChecUrl = null;
            String healthCheckSecureUrl = null;

            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode fieldNode = node.get(fieldName);

                if (!fieldNode.isNull()) {
                    if (ELEM_HOST.equals(fieldName)) {
                        builder.setHostName(fieldNode.asText());
                    } else if (ELEM_INSTANCE_ID.equals(fieldName)) {
                        builder.setInstanceId(fieldNode.asText());
                    } else if (ELEM_APP.equals(fieldName)) {
                        builder.setAppName(fieldNode.asText());
                    } else if (ELEM_IP.equals(fieldName)) {
                        builder.setIPAddr(fieldNode.asText());
                    } else if (ELEM_SID.equals(fieldName)) {
                        builder.setSID(fieldNode.asText());
                    } else if (ELEM_IDENTIFYING_ATTR.equals(fieldName)) {
                        // nothing;
                    } else if (ELEM_STATUS.equals(fieldName)) {
                        builder.setStatus(InstanceStatus.toEnum(fieldNode.asText()));
                    } else if (ELEM_OVERRIDDEN_STATUS.equals(fieldName)) {
                        builder.setOverriddenStatus(InstanceStatus.toEnum(fieldNode.asText()));
                    } else if (ELEM_PORT.equals(fieldName)) {
                        int port = fieldNode.get("$").asInt();
                        boolean enabled = fieldNode.get("@enabled").asBoolean();
                        builder.setPort(port);
                        builder.enablePort(PortType.UNSECURE, enabled);
                    } else if (ELEM_SECURE_PORT.equals(fieldName)) {
                        int port = fieldNode.get("$").asInt();
                        boolean enabled = fieldNode.get("@enabled").asBoolean();
                        builder.setSecurePort(port);
                        builder.enablePort(PortType.SECURE, enabled);
                    } else if (ELEM_COUNTRY_ID.equals(fieldName)) {
                        builder.setCountryId(Integer.valueOf(fieldNode.asText()).intValue());
                    } else if (NODE_DATACENTER.equals(fieldName)) {
                        builder.setDataCenterInfo(mapper.treeToValue(fieldNode, DataCenterInfo.class));
                    } else if (NODE_LEASE.equals(fieldName)) {
                        builder.setLeaseInfo(mapper.treeToValue(fieldNode, LeaseInfo.class));
                    } else if (NODE_METADATA.equals(fieldName)) {
                        Map<String, String> meta = null;
                        Iterator<String> metaNameIt = fieldNode.fieldNames();
                        while (metaNameIt.hasNext()) {
                            String key = StringCache.intern(metaNameIt.next());
                            if (key.equals("@class")) { // For backwards compatibility
                                if (meta == null && !metaNameIt.hasNext()) { // Optimize for empty maps
                                    meta = Collections.emptyMap();
                                }
                            } else {
                                if (meta == null) {
                                    meta = new ConcurrentHashMap<String, String>();
                                }
                                String value = StringCache.intern(fieldNode.get(key).asText());
                                meta.put(key, value);
                            }
                        }
                        if (meta == null) {
                            meta = Collections.emptyMap();
                        }
                        builder.setMetadata(meta);
                    } else if (ELEM_HEALTHCHECKURL.equals(fieldName)) {
                        healthChecUrl = fieldNode.asText();
                    } else if (ELEM_SECHEALTHCHECKURL.equals(fieldName)) {
                        healthCheckSecureUrl = fieldNode.asText();
                    } else if (ELEM_APPGROUPNAME.equals(fieldName)) {
                        builder.setAppGroupName(fieldNode.asText());
                    } else if (ELEM_HOMEPAGEURL.equals(fieldName)) {
                        builder.setHomePageUrlForDeser(fieldNode.asText());
                    } else if (ELEM_STATUSPAGEURL.equals(fieldName)) {
                        builder.setStatusPageUrlForDeser(fieldNode.asText());
                    } else if (ELEM_VIPADDRESS.equals(fieldName)) {
                        builder.setVIPAddressDeser(fieldNode.asText());
                    } else if (ELEM_SECVIPADDRESS.equals(fieldName)) {
                        builder.setSecureVIPAddressDeser(fieldNode.asText());
                    } else if (ELEM_ISCOORDINATINGDISCSOERVER.equals(fieldName)) {
                        builder.setIsCoordinatingDiscoveryServer(fieldNode.asBoolean());
                    } else if (ELEM_LASTUPDATEDTS.equals(fieldName)) {
                        builder.setLastUpdatedTimestamp(fieldNode.asLong());
                    } else if (ELEM_LASTDIRTYTS.equals(fieldName)) {
                        builder.setLastDirtyTimestamp(fieldNode.asLong());
                    } else if (ELEM_ACTIONTYPE.equals(fieldName)) {
                        builder.setActionType(ActionType.valueOf(fieldNode.asText()));
                    } else if (ELEM_ASGNAME.equals(fieldName)) {
                        builder.setASGName(fieldNode.asText());
                    } else {
                        autoUnmarshalEligible(fieldName, fieldNode.asText(), builder.getRawInstance());
                    }
                }
            }
            builder.setHealthCheckUrlsForDeser(healthChecUrl, healthCheckSecureUrl);

            return builder.build();
        }

        protected void autoUnmarshalEligible(String fieldName, String value, Object o) {
            try {
                Class c = o.getClass();
                Field f = null;
                try {
                    f = c.getDeclaredField(fieldName);
                } catch (NoSuchFieldException e) {
                    // TODO XStream version increments metrics counter here
                }
                if (f == null) {
                    return;
                }
                Annotation annotation = f.getAnnotation(Auto.class);
                if (annotation == null) {
                    return;
                }
                f.setAccessible(true);

                Class returnClass = f.getType();
                if (value != null) {
                    if (!String.class.equals(returnClass)) {
                        Method method = returnClass.getDeclaredMethod("valueOf", java.lang.String.class);
                        Object valueObject = method.invoke(returnClass, value);
                        f.set(o, valueObject);
                    } else {
                        f.set(o, value);

                    }
                }
            } catch (Throwable th) {
                logger.error("Error in unmarshalling the object:", th);
            }
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
            JsonNode node = jp.getCodec().readTree(jp);

            Application application = new Application(node.get(ELEM_NAME).asText());

            JsonNode instanceNode = node.get(ELEM_INSTANCE);
            if (instanceNode != null) {
                if (instanceNode instanceof ArrayNode) {
                    ArrayNode instancesNode = (ArrayNode) instanceNode;
                    if (instancesNode != null) {
                        for (JsonNode nextNode : instancesNode) {
                            application.addInstance(mapper.treeToValue(nextNode, InstanceInfo.class));
                        }
                    }
                } else {
                    application.addInstance(mapper.treeToValue(instanceNode, InstanceInfo.class));
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
            Applications apps = new Applications();

            JsonNode node = jp.getCodec().readTree(jp);

            if (node.get(versionDeltaKey) != null) {
                apps.setVersion(node.get(versionDeltaKey).asLong());
            }
            if (node.get(appHashCodeKey) != null) {
                apps.setAppsHashCode(node.get(appHashCodeKey).asText());
            }
            JsonNode appNode = node.get(NODE_APP);
            if (appNode != null) {
                if (appNode instanceof ArrayNode) {
                    ArrayNode appsNode = (ArrayNode) appNode;
                    for (JsonNode item : appsNode) {
                        apps.addApplication(mapper.treeToValue(item, Application.class));
                    }
                } else {
                    apps.addApplication(mapper.treeToValue(appNode, Application.class));
                }
            }
            return apps;
        }
    }
}
