package com.netflix.eureka2.model.toplogy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.model.instance.InstanceInfo;

/**
 * Generate set of services, with a corresponding dependency graph.
 *
 * @author Tomasz Bak
 */
public class ServiceTopologyGenerator {

    private final String topologyId;
    private final String serverId;
    private final List<ApplicationProfile> applicationProfiles;
    private final List<DependencyProfile> dependencyProfiles;
    private final DataCenterInfoGenerator dataCenterInfoGenerator;
    private final double sizeMultiplier;

    // Topology data
    private final List<Application> applications = new ArrayList<>();
    private final Map<ApplicationProfile, List<Application>> applicationsByProfile = new HashMap<>();

    private final Map<String, Service> services = new HashMap<>();

    private final Random random = new Random();

    public ServiceTopologyGenerator(String topologyId,
                                    String serverId,
                                    List<ApplicationProfile> applicationProfiles,
                                    List<DependencyProfile> dependencyProfiles,
                                    DataCenterInfoGenerator dataCenterInfoGenerator,
                                    double sizeMultiplier) {
        this.topologyId = topologyId;
        this.serverId = serverId;
        this.applicationProfiles = applicationProfiles;
        this.dependencyProfiles = dependencyProfiles;
        this.dataCenterInfoGenerator = dataCenterInfoGenerator;
        this.sizeMultiplier = sizeMultiplier;

        generateApplications();
    }

    public Iterator<Application> applicationIterator() {
        return applications.iterator();
    }

    public Interest<InstanceInfo> allApplicationsInterest() {
        List<Interest<InstanceInfo>> interests = new ArrayList<>();
        for (Application application : applications) {
            interests.add(Interests.forApplications(application.getName()));
        }
        return Interests.forSome(interests.toArray(new Interest[interests.size()]));
    }

    /**
     * Iterate through complete service topology generated from the configuration data.
     */
    public Iterator<InstanceInfo> serviceIterator() {
        return new Iterator<InstanceInfo>() {

            private InstanceInfo nextInstanceInfo;
            private int currentAppIdx;
            private int currentServiceIdx;

            @Override
            public boolean hasNext() {
                if (nextInstanceInfo != null) {
                    return true;
                }
                if (currentAppIdx >= applications.size()) {
                    return false;
                }
                Application current = applications.get(currentAppIdx);
                if (currentServiceIdx < current.getProfile().getApplicationSize()) {
                    nextInstanceInfo = addServiceOf(current).getSelfInfo();
                    return true;
                }
                currentServiceIdx = 0;
                currentAppIdx++;
                return hasNext();
            }

            @Override
            public InstanceInfo next() {
                if (hasNext()) {
                    InstanceInfo result = nextInstanceInfo;
                    nextInstanceInfo = null;
                    currentServiceIdx++;
                    return result;
                }
                throw new NoSuchElementException("End of service topology reached");
            }

            @Override
            public void remove() {
            }
        };
    }

    /**
     * Generate infinite number of client interests. The interest set is generated
     * from {@link ServiceTopologyGenerator#dependencyProfiles}, weighted according to
     * the defined quantities.
     */
    public Iterator<Interest<InstanceInfo>> clientInterestIterator() {
        final Iterator<DependencyProfile> dependencyProfileIt = DependencyProfile.streamFrom(dependencyProfiles);
        return new Iterator<Interest<InstanceInfo>>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Interest<InstanceInfo> next() {
                DependencyProfile dependencyProfile = dependencyProfileIt.next();
                Map<ApplicationProfile, Integer> subscriptionMap = dependencyProfile.getSubscriptionsPerProfile();
                List<Interest<InstanceInfo>> interests = new ArrayList<>();
                for (Map.Entry<ApplicationProfile, Integer> entry : subscriptionMap.entrySet()) {
                    List<Application> byProfile = applicationsByProfile.get(entry.getKey());
                    List<Application> selection = randomSubset(byProfile, entry.getValue());
                    for (Application selected : selection) {
                        interests.add(Interests.forApplications(selected.getName()));
                    }
                }
                return Interests.forSome(interests.toArray(new Interest[interests.size()]));
            }

            @Override
            public void remove() {
            }
        };
    }

    public List<InstanceInfo> serviceList() {
        List<InstanceInfo> allServices = new ArrayList<>();
        Iterator<InstanceInfo> it = serviceIterator();
        while (it.hasNext()) {
            allServices.add(it.next());
        }
        return allServices;
    }

    /**
     * For a given instance, generate another instance belonging to the same application,
     * and having the same dependency profile. The actual concrete dependency list is
     * reconstructed, so might be different.
     */
    public InstanceInfo replacementFor(InstanceInfo terminated) {
        Application application = services.get(terminated.getId()).getApplication();
        removeService(terminated);
        return addServiceOf(application).getSelfInfo();
    }

    private void removeService(InstanceInfo terminated) {
        services.remove(terminated.getId());
    }

    private void generateApplications() {
        for (ApplicationProfile profile : applicationProfiles) {
            List<Application> byProfile = new ArrayList<>();
            applicationsByProfile.put(profile, byProfile);

            int quantity = (int) Math.ceil(profile.getQuantity() * sizeMultiplier);
            for (int i = 0; i < quantity; i++) {
                String name = topologyId + '@' + profile.getApplicationName() + "#app_" + i;
                Application application = new Application(name, profile);
                applications.add(application);
                byProfile.add(application);
            }
        }
    }

    private Service addServiceOf(Application application) {
        Service service = Service.createServiceOf(serverId, application, dataCenterInfoGenerator);
        services.put(service.getSelfInfo().getId(), service);
        return service;
    }

    private <T> List<T> randomSubset(List<T> items, int count) {
        int[] itemIdxs = new int[items.size()];
        for (int i = 0; i < itemIdxs.length; i++) {
            itemIdxs[i] = i;
        }
        List<T> result = new ArrayList<>(count);
        int limit = Math.min(itemIdxs.length, count);
        for (int i = 0; i < limit; i++) {
            int pos = random.nextInt(itemIdxs.length - i);
            result.add(items.get(itemIdxs[i + pos]));

            itemIdxs[i + pos] = itemIdxs[i];
        }
        return result;
    }

    public static class ServiceTopologyGeneratorBuilder {
        private String topologyId;
        private List<ApplicationProfile> applicationProfiles;
        private List<DependencyProfile> dependencyProfiles;
        private DataCenterInfoGenerator dataCenterInfoGenerator;
        private double sizeMultiplier;
        private String serverId;

        public ServiceTopologyGeneratorBuilder withTopologyId(String topologyId) {
            this.topologyId = topologyId;
            return this;
        }

        public ServiceTopologyGeneratorBuilder withServerId(String serverId) {
            this.serverId = serverId;
            return this;
        }

        public ServiceTopologyGeneratorBuilder withApplicationProfiles(List<ApplicationProfile> applicationProfiles) {
            this.applicationProfiles = applicationProfiles;
            return this;
        }

        public ServiceTopologyGeneratorBuilder withDependencyProfiles(List<DependencyProfile> dependencyProfiles) {
            this.dependencyProfiles = dependencyProfiles;
            return this;
        }

        public ServiceTopologyGeneratorBuilder withDataCenterInfoGenerator(DataCenterInfoGenerator dataCenterInfoGenerator) {
            this.dataCenterInfoGenerator = dataCenterInfoGenerator;
            return this;
        }

        public ServiceTopologyGeneratorBuilder scaledTo(int numberOfServices) {
            int total = 0;
            for (ApplicationProfile profile : applicationProfiles) {
                total += profile.getServiceCount();
            }
            this.sizeMultiplier = numberOfServices / (double) total;
            return this;
        }

        public ServiceTopologyGenerator build() {
            return new ServiceTopologyGenerator(topologyId, serverId, applicationProfiles, dependencyProfiles, dataCenterInfoGenerator, sizeMultiplier);
        }
    }
}
