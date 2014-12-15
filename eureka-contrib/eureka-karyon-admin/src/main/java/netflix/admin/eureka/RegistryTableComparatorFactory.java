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
                if (o1.getApp() != null && o2.getApp() != null) {
                    return o1.getApp().compareTo(o2.getApp());
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
                return 0;
            }
        });

        columnComparatorAscMap.put(RegistryTableView.Column.Status, new Comparator<InstanceInfo>() {
            @Override
            public int compare(InstanceInfo o1, InstanceInfo o2) {
                if (o1.getStatus() != null && o2.getStatus() != null) {
                    return o1.getStatus().name().compareTo(o2.getStatus().name());
                }
                return 0;
            }
        });


        //  descending comparators
        columnComparatorDescMap.put(RegistryTableView.Column.Application, new Comparator<InstanceInfo>() {
            @Override
            public int compare(InstanceInfo o1, InstanceInfo o2) {
                if (o1.getApp() != null && o2.getApp() != null) {
                    return o2.getApp().compareTo(o1.getApp());
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
                return 0;
            }
        });

        columnComparatorAscMap.put(RegistryTableView.Column.Status, new Comparator<InstanceInfo>() {
            @Override
            public int compare(InstanceInfo o1, InstanceInfo o2) {
                if (o1.getStatus() != null && o2.getStatus() != null) {
                    return o2.getStatus().name().compareTo(o1.getStatus().name());
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
