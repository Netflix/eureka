package com.netflix.discovery.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;

/**
 * Collection of functions operating on {@link Applications} and {@link Application} data
 * structures.
 *
 * @author Tomasz Bak
 * @deprecated Use instead {@link EurekaEntityFunctions}
 */
public final class ApplicationFunctions {

    private ApplicationFunctions() {
    }

    public static Map<String, Application> toApplicationMap(List<InstanceInfo> instances) {
        Map<String, Application> applicationMap = new HashMap<String, Application>();
        for (InstanceInfo instance : instances) {
            String appName = instance.getAppName();
            Application application = applicationMap.get(appName);
            if (application == null) {
                applicationMap.put(appName, application = new Application(appName));
            }
            application.addInstance(instance);
        }
        return applicationMap;
    }

    public static Applications toApplications(Map<String, Application> applicationMap) {
        Applications applications = new Applications();
        for (Application application : applicationMap.values()) {
            applications.addApplication(application);
        }
        return updateMeta(applications);
    }

    public static Set<String> applicationNames(Applications applications) {
        Set<String> names = new HashSet<String>();
        for (Application application : applications.getRegisteredApplications()) {
            names.add(application.getName());
        }
        return names;
    }

    public static Application copyOf(Application application) {
        Application copy = new Application(application.getName());
        for (InstanceInfo instance : application.getInstances()) {
            copy.addInstance(instance);
        }
        return copy;
    }

    public static Application merge(Application first, Application second) {
        if (!first.getName().equals(second.getName())) {
            throw new IllegalArgumentException("Cannot merge applications with different names");
        }
        Application merged = copyOf(first);
        for (InstanceInfo instance : second.getInstances()) {
            switch (instance.getActionType()) {
                case ADDED:
                case MODIFIED:
                    merged.addInstance(instance);
                    break;
                case DELETED:
                    merged.removeInstance(instance);
            }
        }
        return merged;
    }

    public static Applications merge(Applications first, Applications second) {
        Set<String> firstNames = applicationNames(first);
        Set<String> secondNames = applicationNames(second);
        Set<String> allNames = new HashSet<String>(firstNames);
        allNames.addAll(secondNames);

        Applications merged = new Applications();
        for (String appName : allNames) {
            if (firstNames.contains(appName)) {
                if (secondNames.contains(appName)) {
                    merged.addApplication(merge(first.getRegisteredApplications(appName), second.getRegisteredApplications(appName)));
                } else {
                    merged.addApplication(copyOf(first.getRegisteredApplications(appName)));
                }
            } else {
                merged.addApplication(copyOf(second.getRegisteredApplications(appName)));
            }
        }
        return updateMeta(merged);
    }

    public static Applications updateMeta(Applications applications) {
        applications.setVersion(1L);
        applications.setAppsHashCode(applications.getReconcileHashCode());
        return applications;
    }

    public static int countInstances(Applications applications) {
        int count = 0;
        for(Application application: applications.getRegisteredApplications()) {
            count += application.getInstances().size();
        }
        return count;
    }
}
