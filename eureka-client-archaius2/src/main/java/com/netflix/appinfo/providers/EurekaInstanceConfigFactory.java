package com.netflix.appinfo.providers;

import com.netflix.appinfo.EurekaInstanceConfig;

/**
 * An equivalent {@link javax.inject.Provider} interface for {@link com.netflix.appinfo.EurekaInstanceConfig}.
 *
 * Why define this {@link com.netflix.appinfo.providers.EurekaInstanceConfigFactory} instead
 * of using {@link javax.inject.Provider} instead? Provider does not work due to the fact that
 * Guice treats Providers specially.
 *
 * @author David Liu
 */
public interface EurekaInstanceConfigFactory {
    
    EurekaInstanceConfig get();

}
