package com.netflix.discovery.util;

import java.util.List;
import java.util.Map;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;

/**
 * For test use.
 *
 * @author Tomasz Bak
 */
public final class EurekaEntityComparators {

    private EurekaEntityComparators() {
    }

    public static boolean equal(DataCenterInfo first, DataCenterInfo second) {
        if (first == second) {
            return true;
        }
        if (first == null || first == null && second != null) {
            return false;
        }
        if (first.getClass() != second.getClass()) {
            return false;
        }
        if (first instanceof AmazonInfo) {
            return equal((AmazonInfo) first, (AmazonInfo) second);
        }
        return first.getName() == second.getName();
    }

    public static boolean equal(AmazonInfo first, AmazonInfo second) {
        if (first == second) {
            return true;
        }
        if (first == null || first == null && second != null) {
            return false;
        }

        return first.getMetadata().equals(second.getMetadata());
    }

    public static boolean subsetOf(DataCenterInfo first, DataCenterInfo second) {
        if (first == second) {
            return true;
        }
        if (first == null || first == null && second != null) {
            return false;
        }
        if (first.getClass() != second.getClass()) {
            return false;
        }
        if (first instanceof AmazonInfo) {
            return subsetOf((AmazonInfo) first, (AmazonInfo) second);
        }
        return first.getName() == second.getName();
    }

    public static boolean subsetOf(AmazonInfo first, AmazonInfo second) {
        if (first == second) {
            return true;
        }
        if (first == null || first == null && second != null) {
            return false;
        }

        return first.getMetadata().entrySet().containsAll(second.getMetadata().entrySet());
    }


    public static boolean equal(LeaseInfo first, LeaseInfo second) {
        if (first == second) {
            return true;
        }
        if (first == null || first == null && second != null) {
            return false;
        }

        if (first.getDurationInSecs() != second.getDurationInSecs()) {
            return false;
        }
        if (first.getEvictionTimestamp() != second.getEvictionTimestamp()) {
            return false;
        }
        if (first.getRegistrationTimestamp() != second.getRegistrationTimestamp()) {
            return false;
        }
        if (first.getRenewalIntervalInSecs() != second.getRenewalIntervalInSecs()) {
            return false;
        }
        if (first.getRenewalTimestamp() != second.getRenewalTimestamp()) {
            return false;
        }
        if (first.getServiceUpTimestamp() != second.getServiceUpTimestamp()) {
            return false;
        }
        return true;
    }

    public static boolean equal(InstanceInfo first, InstanceInfo second) {
        return equal(first, second, new ResolvedIdEqualFunc());
    }

    public static boolean equal(InstanceInfo first, InstanceInfo second, EqualFunc<InstanceInfo> idEqualFunc) {
        if (first == second) {
            return true;
        }
        if (first == null || first == null && second != null) {
            return false;
        }

        if (first.getCountryId() != second.getCountryId()) {
            return false;
        }
        if (first.getPort() != second.getPort()) {
            return false;
        }
        if (first.getSecurePort() != second.getSecurePort()) {
            return false;
        }
        if (first.getActionType() != second.getActionType()) {
            return false;
        }
        if (first.getAppGroupName() != null ? !first.getAppGroupName().equals(second.getAppGroupName()) : second.getAppGroupName() != null) {
            return false;
        }
        if (!idEqualFunc.equals(first, second)) {
            return false;
        }
        if (first.getSID() != null ? !first.getSID().equals(second.getSID()) : second.getSID() != null) {
            return false;
        }
        if (first.getAppName() != null ? !first.getAppName().equals(second.getAppName()) : second.getAppName() != null) {
            return false;
        }
        if (first.getASGName() != null ? !first.getASGName().equals(second.getASGName()) : second.getASGName() != null) {
            return false;
        }
        if (!equal(first.getDataCenterInfo(), second.getDataCenterInfo())) {
            return false;
        }
        if (first.getHealthCheckUrls() != null ? !first.getHealthCheckUrls().equals(second.getHealthCheckUrls()) : second.getHealthCheckUrls() != null) {
            return false;
        }
        if (first.getHomePageUrl() != null ? !first.getHomePageUrl().equals(second.getHomePageUrl()) : second.getHomePageUrl() != null) {
            return false;
        }
        if (first.getHostName() != null ? !first.getHostName().equals(second.getHostName()) : second.getHostName() != null) {
            return false;
        }
        if (first.getIPAddr() != null ? !first.getIPAddr().equals(second.getIPAddr()) : second.getIPAddr() != null) {
            return false;
        }
        if (!equal(first.getLeaseInfo(), second.getLeaseInfo())) {
            return false;
        }
        if (!equal(first.getMetadata(), second.getMetadata())) {
            return false;
        }
        if (first.getHealthCheckUrls() != null ? !first.getHealthCheckUrls().equals(second.getHealthCheckUrls()) : second.getHealthCheckUrls() != null) {
            return false;
        }
        if (first.getVIPAddress() != null ? !first.getVIPAddress().equals(second.getVIPAddress()) : second.getVIPAddress() != null) {
            return false;
        }
        if (first.getSecureVipAddress() != null ? !first.getSecureVipAddress().equals(second.getSecureVipAddress()) : second.getSecureVipAddress() != null) {
            return false;
        }
        if (first.getStatus() != null ? !first.getStatus().equals(second.getStatus()) : second.getStatus() != null) {
            return false;
        }
        if (first.getStatusPageUrl() != null ? !first.getStatusPageUrl().equals(second.getStatusPageUrl()) : second.getStatusPageUrl() != null) {
            return false;
        }
        if (first.getLastDirtyTimestamp() != null ? !first.getLastDirtyTimestamp().equals(second.getLastDirtyTimestamp()) : second.getLastDirtyTimestamp() != null) {
            return false;
        }
        if (first.getLastUpdatedTimestamp()!= second.getLastUpdatedTimestamp()) {
            return false;
        }
        if (first.isCoordinatingDiscoveryServer() != null ? !first.isCoordinatingDiscoveryServer().equals(second.isCoordinatingDiscoveryServer()) : second.isCoordinatingDiscoveryServer() != null) {
            return false;
        }
        return true;
    }


    private static boolean idEqual(InstanceInfo first, InstanceInfo second) {
        return first.getId().equals(second.getId());
    }

    public static boolean equalMini(InstanceInfo first, InstanceInfo second) {
        if (first == second) {
            return true;
        }
        if (first == null || first == null && second != null) {
            return false;
        }

        if (first.getPort() != second.getPort()) {
            return false;
        }
        if (first.getSecurePort() != second.getSecurePort()) {
            return false;
        }
        if (first.getActionType() != second.getActionType()) {
            return false;
        }
        if (first.getInstanceId() != null ? !first.getInstanceId().equals(second.getInstanceId()) : second.getInstanceId() != null) {
            return false;
        }
        if (first.getAppName() != null ? !first.getAppName().equals(second.getAppName()) : second.getAppName() != null) {
            return false;
        }
        if (first.getASGName() != null ? !first.getASGName().equals(second.getASGName()) : second.getASGName() != null) {
            return false;
        }
        if (!subsetOf(first.getDataCenterInfo(), second.getDataCenterInfo())) {
            return false;
        }
        if (first.getHostName() != null ? !first.getHostName().equals(second.getHostName()) : second.getHostName() != null) {
            return false;
        }
        if (first.getIPAddr() != null ? !first.getIPAddr().equals(second.getIPAddr()) : second.getIPAddr() != null) {
            return false;
        }
        if (first.getVIPAddress() != null ? !first.getVIPAddress().equals(second.getVIPAddress()) : second.getVIPAddress() != null) {
            return false;
        }
        if (first.getSecureVipAddress() != null ? !first.getSecureVipAddress().equals(second.getSecureVipAddress()) : second.getSecureVipAddress() != null) {
            return false;
        }
        if (first.getStatus() != null ? !first.getStatus().equals(second.getStatus()) : second.getStatus() != null) {
            return false;
        }
        if (first.getLastUpdatedTimestamp()!= second.getLastUpdatedTimestamp()) {
            return false;
        }
        return true;
    }

    public static boolean equal(Application first, Application second) {
        if (first == second) {
            return true;
        }
        if (first == null || first == null && second != null) {
            return false;
        }

        if (first.getName() != null ? !first.getName().equals(second.getName()) : second.getName() != null) {
            return false;
        }
        List<InstanceInfo> firstInstanceInfos = first.getInstances();
        List<InstanceInfo> secondInstanceInfos = second.getInstances();
        if (firstInstanceInfos == null && secondInstanceInfos == null) {
            return true;
        }
        if (firstInstanceInfos == null || secondInstanceInfos == null || firstInstanceInfos.size() != secondInstanceInfos.size()) {
            return false;
        }
        for (InstanceInfo firstInstanceInfo : firstInstanceInfos) {
            InstanceInfo secondInstanceInfo = second.getByInstanceId(firstInstanceInfo.getId());
            if (!equal(firstInstanceInfo, secondInstanceInfo)) {
                return false;
            }
        }

        return true;
    }

    public static boolean equal(Applications first, Applications second) {
        if (first == second) {
            return true;
        }
        if (first == null || first == null && second != null) {
            return false;
        }
        List<Application> firstApps = first.getRegisteredApplications();
        List<Application> secondApps = second.getRegisteredApplications();
        if (firstApps == null && secondApps == null) {
            return true;
        }
        if (firstApps == null || secondApps == null || firstApps.size() != secondApps.size()) {
            return false;
        }
        for (Application firstApp : firstApps) {
            Application secondApp = second.getRegisteredApplications(firstApp.getName());
            if (!equal(firstApp, secondApp)) {
                return false;
            }
        }

        return true;
    }

    private static boolean equal(Map<String, String> first, Map<String, String> second) {
        if (first == second) {
            return true;
        }
        if (first == null || first == null && second != null || first.size() != second.size()) {
            return false;
        }
        for (Map.Entry<String, String> entry : first.entrySet()) {
            if (!second.containsKey(entry.getKey())) {
                return false;
            }
            String firstValue = entry.getValue();
            String secondValue = second.get(entry.getKey());
            if (!firstValue.equals(secondValue)) {
                return false;
            }
        }
        return true;
    }

    public interface EqualFunc<T> {
        boolean equals(T first, T second);
    }

    public static class RawIdEqualFunc implements EqualFunc<InstanceInfo> {
        @Override
        public boolean equals(InstanceInfo first, InstanceInfo second) {
            return first.getInstanceId() != null
                    ? first.getInstanceId().equals(second.getInstanceId())
                    : second.getInstanceId() == null;
        }
    }

    public static class RawIdHandleEmptyEqualFunc implements EqualFunc<InstanceInfo> {
        @Override
        public boolean equals(InstanceInfo first, InstanceInfo second) {
            String firstId = (first.getInstanceId() == null || first.getInstanceId().isEmpty())
                    ? null
                    : first.getInstanceId();

            String secondId = (second.getInstanceId() == null || second.getInstanceId().isEmpty())
                    ? null
                    : second.getInstanceId();

            return firstId != null
                    ? firstId.equals(secondId)
                    : secondId == null;
        }
    }

    public static class ResolvedIdEqualFunc implements EqualFunc<InstanceInfo> {
        @Override
        public boolean equals(InstanceInfo first, InstanceInfo second) {
            return first.getId() != null
                    ? first.getId().equals(second.getId())
                    : second.getId() == null;
        }
    }
}
