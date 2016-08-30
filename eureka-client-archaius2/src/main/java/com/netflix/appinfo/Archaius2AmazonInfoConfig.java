package com.netflix.appinfo;

import com.netflix.archaius.api.Config;
import com.netflix.archaius.api.annotations.ConfigurationSource;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.netflix.appinfo.PropertyBasedAmazonInfoConfigConstants.*;

/**
 * @author David Liu
 */
@Singleton
@ConfigurationSource(Values.DEFAULT_CONFIG_FILE_NAME)
public class Archaius2AmazonInfoConfig implements AmazonInfoConfig {

    private final Config config;
    private final String namespace;

    @Inject
    public Archaius2AmazonInfoConfig(Config config) {
        this(config, Values.DEFAULT_NAMESPACE);
    }


    public Archaius2AmazonInfoConfig(Config config, String namespace) {
        this.namespace = namespace;
        this.config = config.getPrefixedView(namespace);
    }


    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public boolean shouldLogAmazonMetadataErrors() {
        return config.getBoolean(LOG_METADATA_ERROR_KEY, false);
    }

    @Override
    public int getReadTimeout() {
        return config.getInteger(READ_TIMEOUT_KEY, Values.DEFAULT_READ_TIMEOUT);
    }

    @Override
    public int getConnectTimeout() {
        return config.getInteger(CONNECT_TIMEOUT_KEY, Values.DEFAULT_CONNECT_TIMEOUT);
    }

    @Override
    public int getNumRetries() {
        return config.getInteger(NUM_RETRIES_KEY, Values.DEFAULT_NUM_RETRIES);
    }

    @Override
    public boolean shouldFailFastOnFirstLoad() {
        return config.getBoolean(FAIL_FAST_ON_FIRST_LOAD_KEY, true);
    }
}
