package com.netflix.appinfo;

import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.CommonConstants;
import com.netflix.discovery.internal.util.Archaius1Utils;

import static com.netflix.appinfo.PropertyBasedAmazonInfoConfigConstants.*;

/**
 * @author David Liu
 */
public class Archaius1AmazonInfoConfig implements AmazonInfoConfig {

    private final DynamicPropertyFactory configInstance;
    private final String namespace;

    public Archaius1AmazonInfoConfig(String namespace) {
        this.namespace = namespace.endsWith(".")
                ? namespace
                : namespace + ".";

        this.configInstance = Archaius1Utils.initConfig(CommonConstants.CONFIG_FILE_NAME);
    }


    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public boolean shouldLogAmazonMetadataErrors() {
        return configInstance.getBooleanProperty(namespace + LOG_METADATA_ERROR_KEY, false).get();
    }

    @Override
    public int getReadTimeout() {
        return configInstance.getIntProperty(namespace + READ_TIMEOUT_KEY, Values.DEFAULT_READ_TIMEOUT).get();
    }

    @Override
    public int getConnectTimeout() {
        return configInstance.getIntProperty(namespace + CONNECT_TIMEOUT_KEY, Values.DEFAULT_CONNECT_TIMEOUT).get();
    }

    @Override
    public int getNumRetries() {
        return configInstance.getIntProperty(namespace + NUM_RETRIES_KEY, Values.DEFAULT_NUM_RETRIES).get();
    }

    @Override
    public boolean shouldFailFastOnFirstLoad() {
        return configInstance.getBooleanProperty(namespace + FAIL_FAST_ON_FIRST_LOAD_KEY, true).get();
    }

    @Override
    public boolean shouldValidateInstanceId() {
        return configInstance.getBooleanProperty(namespace + SHOULD_VALIDATE_INSTANCE_ID_KEY, true).get();
    }
}
