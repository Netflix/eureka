package com.netflix.appinfo;

import com.netflix.archaius.api.Config;
import com.netflix.archaius.api.annotations.ConfigurationSource;
import com.netflix.discovery.CommonConstants;
import com.netflix.discovery.internal.util.InternalPrefixedConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.netflix.appinfo.PropertyBasedAmazonInfoConfigConstants.*;

/**
 * @author David Liu
 */
@Singleton
@ConfigurationSource(CommonConstants.CONFIG_FILE_NAME)
public class Archaius2AmazonInfoConfig implements AmazonInfoConfig {

    private final Config configInstance;
    private final InternalPrefixedConfig prefixedConfig;
    private final String namespace;

    @Inject
    public Archaius2AmazonInfoConfig(Config configInstance) {
        this(configInstance, CommonConstants.DEFAULT_CONFIG_NAMESPACE);
    }


    public Archaius2AmazonInfoConfig(Config configInstance, String namespace) {
        this.namespace = namespace;
        this.configInstance = configInstance;
        this.prefixedConfig = new InternalPrefixedConfig(configInstance, namespace);
    }


    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public boolean shouldLogAmazonMetadataErrors() {
        return prefixedConfig.getBoolean(LOG_METADATA_ERROR_KEY, false);
    }

    @Override
    public int getReadTimeout() {
        return prefixedConfig.getInteger(READ_TIMEOUT_KEY, Values.DEFAULT_READ_TIMEOUT);
    }

    @Override
    public int getConnectTimeout() {
        return prefixedConfig.getInteger(CONNECT_TIMEOUT_KEY, Values.DEFAULT_CONNECT_TIMEOUT);
    }

    @Override
    public int getNumRetries() {
        return prefixedConfig.getInteger(NUM_RETRIES_KEY, Values.DEFAULT_NUM_RETRIES);
    }

    @Override
    public boolean shouldFailFastOnFirstLoad() {
        return prefixedConfig.getBoolean(FAIL_FAST_ON_FIRST_LOAD_KEY, true);
    }

    @Override
    public boolean shouldValidateInstanceId() {
        return prefixedConfig.getBoolean(SHOULD_VALIDATE_INSTANCE_ID_KEY, true);
    }
}
