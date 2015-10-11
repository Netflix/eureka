/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.discovery.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.ActionType;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.EurekaEntityTransformers.Transformer;

/**
 * Collection of functions operating on {@link Applications} and {@link Application} data
 * structures. The functions are organized into groups with common prefix name:
 * <ul>
 *     <li>select, take - queries over Eureka entity objects</li>
 *     <li>to - Eureka entity object transformers</li>
 *     <li>copy - copy Eureka entities, with aggregated {@link InstanceInfo} objects copied by reference</li>
 *     <li>deepCopy - copy Eureka entities, with aggregated {@link InstanceInfo} objects copied by value</li>
 *     <li>merge - merge two identical data structures</li>
 *     <li>count - statistical functions</li>
 *     <li>comparator - comparators for the domain objects</li>
 * </ul>
 *
 * @author Tomasz Bak
 */
public final class EurekaEntityFunctions {

    private EurekaEntityFunctions() {
    }

    public static Set<String> selectApplicationNames(Applications applications) {
        Set<String> result = new HashSet<>();
        for (Application app : applications.getRegisteredApplications()) {
            result.add(app.getName());
        }
        return result;
    }

    public static Map<String, InstanceInfo> selectInstancesMappedById(Application application) {
        Map<String, InstanceInfo> result = new HashMap<>();
        for (InstanceInfo instance : application.getInstances()) {
            result.put(instance.getId(), instance);
        }
        return result;
    }

    public static InstanceInfo selectInstance(Applications applications, String id) {
        for (Application application : applications.getRegisteredApplications()) {
            for (InstanceInfo instance : application.getInstances()) {
                if (instance.getId().equals(id)) {
                    return instance;
                }
            }
        }
        return null;
    }

    public static InstanceInfo selectInstance(Applications applications, String appName, String id) {
        Application application = applications.getRegisteredApplications(appName);
        if (application != null) {
            for (InstanceInfo instance : application.getInstances()) {
                if (instance.getId().equals(id)) {
                    return instance;
                }
            }
        }
        return null;
    }

    public static InstanceInfo takeFirst(Applications applications) {
        for (Application application : applications.getRegisteredApplications()) {
            if (!application.getInstances().isEmpty()) {
                return application.getInstances().get(0);
            }
        }
        return null;
    }

    public static Collection<InstanceInfo> selectAll(Applications applications) {
        List<InstanceInfo> all = new ArrayList<>();
        for (Application a : applications.getRegisteredApplications()) {
            all.addAll(a.getInstances());
        }
        return all;
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

    public static Applications toApplications(InstanceInfo... instances) {
        return toApplications(Arrays.asList(instances));
    }

    public static Applications toApplications(List<InstanceInfo> instances) {
        Applications result = new Applications();
        for (InstanceInfo instance : instances) {
            Application app = result.getRegisteredApplications(instance.getAppName());
            if (app == null) {
                app = new Application(instance.getAppName());
                result.addApplication(app);
            }
            app.addInstance(instance);
        }
        return updateMeta(result);
    }

    public static Applications copyApplications(Applications source) {
        Applications result = new Applications();
        copyApplications(source, result);
        return updateMeta(result);
    }

    public static void copyApplications(Applications source, Applications result) {
        if (source != null) {
            for (Application app : source.getRegisteredApplications()) {
                result.addApplication(new Application(app.getName(), app.getInstances()));
            }
        }
    }

    public static Application copyApplication(Application application) {
        Application copy = new Application(application.getName());
        for (InstanceInfo instance : application.getInstances()) {
            copy.addInstance(instance);
        }
        return copy;
    }

    public static void copyApplication(Application source, Application result) {
        if (source != null) {
            for (InstanceInfo instance : source.getInstances()) {
                result.addInstance(instance);
            }
        }
    }

    public static void copyInstances(Collection<InstanceInfo> instances, Applications result) {
        if (instances != null) {
            for (InstanceInfo instance : instances) {
                Application app = result.getRegisteredApplications(instance.getAppName());
                if (app == null) {
                    app = new Application(instance.getAppName());
                    result.addApplication(app);
                }
                app.addInstance(instance);
            }
        }
    }

    public static Collection<InstanceInfo> copyInstances(Collection<InstanceInfo> instances, ActionType actionType) {
        List<InstanceInfo> result = new ArrayList<>();
        for (InstanceInfo instance : instances) {
            result.add(copyInstance(instance, actionType));
        }
        return result;
    }

    public static InstanceInfo copyInstance(InstanceInfo original, ActionType actionType) {
        InstanceInfo copy = new InstanceInfo(original);
        copy.setActionType(actionType);
        return copy;
    }

    public static void deepCopyApplication(Application source, Application result, Transformer<InstanceInfo> transformer) {
        for (InstanceInfo instance : source.getInstances()) {
            InstanceInfo copy = transformer.apply(instance);
            if (copy == instance) {
                copy = new InstanceInfo(instance);
            }
            result.addInstance(copy);
        }
    }

    public static Application deepCopyApplication(Application source) {
        Application result = new Application(source.getName());
        deepCopyApplication(source, result, EurekaEntityTransformers.<InstanceInfo>identity());
        return result;
    }

    public static Applications deepCopyApplications(Applications source) {
        Applications result = new Applications();
        for (Application application : source.getRegisteredApplications()) {
            result.addApplication(deepCopyApplication(application));
        }
        return updateMeta(result);
    }

    public static Applications mergeApplications(Applications first, Applications second) {
        Set<String> firstNames = selectApplicationNames(first);
        Set<String> secondNames = selectApplicationNames(second);
        Set<String> allNames = new HashSet<String>(firstNames);
        allNames.addAll(secondNames);

        Applications merged = new Applications();
        for (String appName : allNames) {
            if (firstNames.contains(appName)) {
                if (secondNames.contains(appName)) {
                    merged.addApplication(mergeApplication(first.getRegisteredApplications(appName), second.getRegisteredApplications(appName)));
                } else {
                    merged.addApplication(copyApplication(first.getRegisteredApplications(appName)));
                }
            } else {
                merged.addApplication(copyApplication(second.getRegisteredApplications(appName)));
            }
        }
        return updateMeta(merged);
    }

    public static Application mergeApplication(Application first, Application second) {
        if (!first.getName().equals(second.getName())) {
            throw new IllegalArgumentException("Cannot merge applications with different names");
        }
        Application merged = copyApplication(first);
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

    public static Applications updateMeta(Applications applications) {
        applications.setVersion(1L);
        applications.setAppsHashCode(applications.getReconcileHashCode());
        return applications;
    }

    public static int countInstances(Applications applications) {
        int count = 0;
        for (Application application : applications.getRegisteredApplications()) {
            count += application.getInstances().size();
        }
        return count;
    }

    public static Comparator<InstanceInfo> comparatorByAppNameAndId() {
        return INSTANCE_APP_ID_COMPARATOR;
    }

    private static class InstanceAppIdComparator implements Comparator<InstanceInfo> {
        @Override
        public int compare(InstanceInfo o1, InstanceInfo o2) {
            int ac = compareStrings(o1.getAppName(), o2.getAppName());
            if (ac != 0) {
                return ac;
            }
            return compareStrings(o1.getId(), o2.getId());
        }

        private int compareStrings(String s1, String s2) {
            if (s1 == null) {
                if (s2 != null) {
                    return -1;
                }
            }
            if (s2 == null) {
                return 1;
            }
            return s1.compareTo(s2);
        }
    }

    private static final Comparator<InstanceInfo> INSTANCE_APP_ID_COMPARATOR = new InstanceAppIdComparator();
}
