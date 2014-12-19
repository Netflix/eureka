package netflix.admin.eureka;

import com.netflix.eureka2.registry.InstanceInfo;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class RegistryTableComparatorFactory {
    private static Map<RegistryTableView.Column, Comparator<InstanceInfo>> columnComparatorAscMap = new HashMap<>(10);
    private static Map<RegistryTableView.Column, Comparator<InstanceInfo>> columnComparatorDescMap = new HashMap<>(10);

    static {
        columnComparatorAscMap.put(RegistryTableView.Column.Application, new Comparator<InstanceInfo>() {
            @Override
            public int compare(InstanceInfo o1, InstanceInfo o2) {
                if (o1 != null && o2 != null && o1.getApp() != null && o2.getApp() != null) {
                    return o1.getApp().compareToIgnoreCase(o2.getApp());
                }
                if (o1 != null && o1.getApp() != null) {
                    return 1;
                }
                if (o2 != null && o2.getApp() != null) {
                    return -1;
                }
                return 0;
            }
        });

        columnComparatorAscMap.put(RegistryTableView.Column.Hostname, new Comparator<InstanceInfo>() {
            @Override
            public int compare(InstanceInfo o1, InstanceInfo o2) {
                final String hostName1 = RegistryTableView.extractHostname(o1);
                final String hostName2 = RegistryTableView.extractHostname(o2);
                if (!hostName1.isEmpty() && !hostName2.isEmpty()) {
                    return hostName1.compareTo(hostName2);
                }
                if (!hostName1.isEmpty()) {
                    return 1;
                }
                if (!hostName2.isEmpty()) {
                    return -1;
                }
                return 0;
            }
        });

        columnComparatorAscMap.put(RegistryTableView.Column.Status, new Comparator<InstanceInfo>() {
            @Override
            public int compare(InstanceInfo o1, InstanceInfo o2) {
                if (o1 != null && o2 != null && o1.getStatus() != null && o2.getStatus() != null) {
                    return o1.getStatus().name().compareTo(o2.getStatus().name());
                }
                if (o1 != null && o1.getStatus() != null) {
                    return 1;
                }
                if (o2 != null && o2.getStatus() != null) {
                    return -1;
                }
                return 0;
            }
        });

        columnComparatorAscMap.put(RegistryTableView.Column.VipAddress, new Comparator<InstanceInfo>() {
            @Override
            public int compare(InstanceInfo o1, InstanceInfo o2) {
                if (o1 != null && o2 != null && o1.getVipAddress() != null && o2.getVipAddress() != null) {
                    return o1.getVipAddress().compareTo(o2.getVipAddress());
                }
                if (o1 != null && o1.getVipAddress() != null) {
                    return 1;
                }
                if (o2 != null && o2.getVipAddress() != null) {
                    return -1;
                }
                return 0;
            }
        });

        //  descending comparators
        columnComparatorDescMap.put(RegistryTableView.Column.Application, new Comparator<InstanceInfo>() {
            @Override
            public int compare(InstanceInfo o1, InstanceInfo o2) {
                if (o1 != null && o2 != null && o1.getApp() != null && o2.getApp() != null) {
                    return o2.getApp().compareToIgnoreCase(o1.getApp());
                }
                if (o1 != null && o1.getApp() != null) {
                    return -1;
                }
                if (o2 != null && o2.getApp() != null) {
                    return 1;
                }
                return 0;
            }
        });

        columnComparatorDescMap.put(RegistryTableView.Column.Hostname, new Comparator<InstanceInfo>() {
            @Override
            public int compare(InstanceInfo o1, InstanceInfo o2) {
                final String hostName1 = RegistryTableView.extractHostname(o1);
                final String hostName2 = RegistryTableView.extractHostname(o2);
                if (!hostName1.isEmpty() && !hostName2.isEmpty()) {
                    return hostName2.compareTo(hostName1);
                }
                if (!hostName1.isEmpty()) {
                    return -1;
                }
                if (!hostName2.isEmpty()) {
                    return 1;
                }
                return 0;
            }
        });

        columnComparatorDescMap.put(RegistryTableView.Column.Status, new Comparator<InstanceInfo>() {
            @Override
            public int compare(InstanceInfo o1, InstanceInfo o2) {
                if (o1 != null && o2 != null && o1.getStatus() != null && o2.getStatus() != null) {
                    return o2.getStatus().name().compareTo(o1.getStatus().name());
                }
                if (o1 != null && o1.getStatus() != null) {
                    return -1;
                }
                if (o2 != null && o2.getStatus() != null) {
                    return 1;
                }
                return 0;
            }
        });

        columnComparatorDescMap.put(RegistryTableView.Column.VipAddress, new Comparator<InstanceInfo>() {
            @Override
            public int compare(InstanceInfo o1, InstanceInfo o2) {
                if (o1 != null && o2 != null && o1.getVipAddress() != null && o2.getVipAddress() != null) {
                    return o2.getVipAddress().compareTo(o1.getVipAddress());
                }
                if (o1 != null && o1.getVipAddress() != null) {
                    return -1;
                }
                if (o2 != null && o2.getVipAddress() != null) {
                    return 1;
                }
                return 0;
            }
        });
    }

    public static Comparator<InstanceInfo> makeComparator(RegistryTableView.Column column, boolean isDescending) {
        if (isDescending) {
            return columnComparatorDescMap.get(column);
        }
        return columnComparatorAscMap.get(column);
    }
}
