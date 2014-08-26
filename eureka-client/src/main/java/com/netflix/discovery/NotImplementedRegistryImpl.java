package com.netflix.discovery;

import com.netflix.discovery.shared.Applications;

import javax.inject.Singleton;

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
