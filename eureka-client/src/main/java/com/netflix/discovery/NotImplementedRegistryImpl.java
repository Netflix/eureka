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
        throw new UnsupportedOperationException("Backup registry not implemented.");
    }

    @Override
    public Applications fetchRegistry(String[] includeRemoteRegions) {
        throw new UnsupportedOperationException("Backup registry not implemented.");
    }
}
