package com.netflix.discovery;

import java.util.HashMap;
import java.util.Map;

import com.google.inject.Singleton;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;

/**
 * @author Nitesh Kant
 */
@Singleton
public class MockBackupRegistry implements BackupRegistry {

    private Map<String, Applications> remoteRegionVsApps = new HashMap<String, Applications>();
    private Applications localRegionApps;

    @Override
    public Applications fetchRegistry() {
        if (null == localRegionApps) {
            return new Applications();
        }
        return localRegionApps;
    }

    @Override
    public Applications fetchRegistry(String[] includeRemoteRegions) {
        Applications toReturn = new Applications();
        for (Application application : localRegionApps.getRegisteredApplications()) {
            toReturn.addApplication(application);
        }
        for (String region : includeRemoteRegions) {
            Applications applications = remoteRegionVsApps.get(region);
            if (null != applications) {
                for (Application application : applications.getRegisteredApplications()) {
                    toReturn.addApplication(application);
                }
            }
        }
        return toReturn;
    }

    public Applications getLocalRegionApps() {
        return localRegionApps;
    }

    public Map<String, Applications> getRemoteRegionVsApps() {
        return remoteRegionVsApps;
    }

    public void setRemoteRegionVsApps(Map<String, Applications> remoteRegionVsApps) {
        this.remoteRegionVsApps = remoteRegionVsApps;
    }

    public void setLocalRegionApps(Applications localRegionApps) {
        this.localRegionApps = localRegionApps;
    }
}
