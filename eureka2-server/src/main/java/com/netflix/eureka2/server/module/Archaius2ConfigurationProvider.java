package com.netflix.eureka2.server.module;

import com.google.common.base.Supplier;
import com.netflix.archaius.Config;
import com.netflix.governator.configuration.ConfigurationKey;
import com.netflix.governator.configuration.ConfigurationProvider;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Date;

/**
 * Copied from unreleased Governator to provide support for @Configuration annotation. Remove once the base
 * code is release in newer Governator versions.
 *
 * @author David Liu
 */
@Singleton
public class Archaius2ConfigurationProvider implements ConfigurationProvider {
    private final Config config;

    @Inject
    public Archaius2ConfigurationProvider(Config config) {
        this.config = config;
    }

    @Override
    public Supplier<Boolean> getBooleanSupplier(final ConfigurationKey propName, final Boolean defaultValue) {
        return new Supplier<Boolean>() {
            @Override
            public Boolean get() {
                return config.getBoolean(propName.getRawKey(), defaultValue);
            }
        };
    }

    @Override
    public Supplier<Date> getDateSupplier(final ConfigurationKey propName, final Date defaultValue) {
        return new Supplier<Date>() {
            @Override
            public Date get() {
//                return config.getBoolean(propName.getRawKey(), defaultValue);
                return null;
            }
        };
    }

    @Override
    public Supplier<Double> getDoubleSupplier(final ConfigurationKey propName, final Double defaultValue) {
        return new Supplier<Double>() {
            @Override
            public Double get() {
                return config.getDouble(propName.getRawKey(), defaultValue);
            }
        };
    }

    @Override
    public Supplier<Integer> getIntegerSupplier(final ConfigurationKey propName, final Integer defaultValue) {
        return new Supplier<Integer>() {
            @Override
            public Integer get() {
                return config.getInteger(propName.getRawKey(), defaultValue);
            }
        };
    }

    @Override
    public Supplier<Long> getLongSupplier(final ConfigurationKey propName, final Long defaultValue) {
        return new Supplier<Long>() {
            @Override
            public Long get() {
                return config.getLong(propName.getRawKey(), defaultValue);
            }
        };
    }

    @Override
    public <T> Supplier<T> getObjectSupplier(final ConfigurationKey propName, final T defaultValue, final Class<T> type) {
        return new Supplier<T>() {
            @Override
            public T get() {
                return config.get(type, propName.getRawKey(), defaultValue);
            }
        };
    }

    @Override
    public Supplier<String> getStringSupplier(final ConfigurationKey propName, final String defaultValue) {
        return new Supplier<String>() {
            @Override
            public String get() {
                return config.getString(propName.getRawKey(), defaultValue);
            }
        };
    }

    @Override
    public boolean has(ConfigurationKey propName) {
        return true;
    }
}
