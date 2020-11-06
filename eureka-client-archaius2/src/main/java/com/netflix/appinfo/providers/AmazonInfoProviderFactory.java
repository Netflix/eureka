package com.netflix.appinfo.providers;

import com.netflix.appinfo.AmazonInfo;

import javax.inject.Provider;

public interface AmazonInfoProviderFactory {
    Provider<AmazonInfo> get();
}
