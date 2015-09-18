package com.netflix.discovery;

import javax.inject.Singleton;

import com.netflix.discovery.shared.Applications;

/**
 * @author Nitesh Kant
 */
@Singleton
public class NotImplementedRegistryImpl implements BackupRegistry {

    @Override
    public Applications fetchRegistry() {
        return null;
    }

    @Override
    public Applications fetchRegistry(String[] includeRemoteRegions) {
        return null;
    }
}
