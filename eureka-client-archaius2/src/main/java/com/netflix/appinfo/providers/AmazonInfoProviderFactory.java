package com.netflix.appinfo.providers;

import com.netflix.appinfo.AmazonInfo;

import jakarta.inject.Provider;

public interface AmazonInfoProviderFactory {
    Provider<AmazonInfo> get();
}
