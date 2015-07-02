package netflix.adminresources.resources.eureka.registry;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;

public final class RegistryTableComparatorFactory {

    private static final Map<RegistryTableView.Column, Comparator<InstanceInfoSummary>> columnComparatorAscMap = new EnumMap<>(RegistryTableView.Column.class);
    private static final Map<RegistryTableView.Column, Comparator<InstanceInfoSummary>> columnComparatorDescMap = new EnumMap<>(RegistryTableView.Column.class);

    static {
        columnComparatorAscMap.put(RegistryTableView.Column.Application, new Comparator<InstanceInfoSummary>() {
            @Override
            public int compare(InstanceInfoSummary o1, InstanceInfoSummary o2) {
                if (o1 != null && o2 != null && o1.getApplication() != null && o2.getApplication() != null) {
                    return o1.getApplication().compareToIgnoreCase(o2.getApplication());
                }
                if (o1 != null && o1.getApplication() != null) {
                    return 1;
                }
                if (o2 != null && o2.getApplication() != null) {
                    return -1;
                }
                return 0;
            }
        });

        columnComparatorAscMap.put(RegistryTableView.Column.Hostname, new Comparator<InstanceInfoSummary>() {
            @Override
            public int compare(InstanceInfoSummary o1, InstanceInfoSummary o2) {
                final String hostName1 = o1.getIpAddress();
                final String hostName2 = o1.getIpAddress();
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

        columnComparatorAscMap.put(RegistryTableView.Column.Status, new Comparator<InstanceInfoSummary>() {
            @Override
            public int compare(InstanceInfoSummary o1, InstanceInfoSummary o2) {
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

        columnComparatorAscMap.put(RegistryTableView.Column.VipAddress, new Comparator<InstanceInfoSummary>() {
            @Override
            public int compare(InstanceInfoSummary o1, InstanceInfoSummary o2) {
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
        columnComparatorDescMap.put(RegistryTableView.Column.Application, new Comparator<InstanceInfoSummary>() {
            @Override
            public int compare(InstanceInfoSummary o1, InstanceInfoSummary o2) {
                if (o1 != null && o2 != null && o1.getApplication() != null && o2.getApplication() != null) {
                    return o2.getApplication().compareToIgnoreCase(o1.getApplication());
                }
                if (o1 != null && o1.getApplication() != null) {
                    return -1;
                }
                if (o2 != null && o2.getApplication() != null) {
                    return 1;
                }
                return 0;
            }
        });

        columnComparatorDescMap.put(RegistryTableView.Column.Hostname, new Comparator<InstanceInfoSummary>() {
            @Override
            public int compare(InstanceInfoSummary o1, InstanceInfoSummary o2) {
                final String hostName1 = o1.getIpAddress();
                final String hostName2 = o1.getIpAddress();
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

        columnComparatorDescMap.put(RegistryTableView.Column.Status, new Comparator<InstanceInfoSummary>() {
            @Override
            public int compare(InstanceInfoSummary o1, InstanceInfoSummary o2) {
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

        columnComparatorDescMap.put(RegistryTableView.Column.VipAddress, new Comparator<InstanceInfoSummary>() {
            @Override
            public int compare(InstanceInfoSummary o1, InstanceInfoSummary o2) {
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

    private RegistryTableComparatorFactory() {
    }

    public static Comparator<InstanceInfoSummary> makeComparator(RegistryTableView.Column column, boolean isDescending) {
        if (isDescending) {
            return columnComparatorDescMap.get(column);
        }
        return columnComparatorAscMap.get(column);
    }
}
