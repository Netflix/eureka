package netflix.admin.eureka;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.inject.Inject;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.datacenter.AwsDataCenterInfo;
import netflix.adminresources.tableview.TableViewResource;

import java.util.*;

public class RegistryTableView implements TableViewResource {
    private final Gson gson;
    private String searchTerm;
    private InstanceRegistryCache registryCache;

    private PageInfo currentPage;
    private int totalRecords;
    private int numFilteredRecords;

    public static enum Column {
        Application,
        InstanceId,
        Status,
        IpAddress,
        VipAddress,
        Hostname;

        public static List<String> getAllColumnNames() {
            List<String> columns = new ArrayList<String>();
            for (Column col : Column.values()) {
                columns.add(col.name());
            }
            return columns;
        }
    }

    private static class PageInfo {
        int startIndex;
        int count;
        Column sortColumn;
        boolean sortDescending;

        private PageInfo(int startIndex, int count) {
            this.startIndex = startIndex;
            this.count = count;
        }

        private PageInfo(Column sortColumn, boolean sortDescending) {
            this.sortColumn = sortColumn;
            this.sortDescending = sortDescending;
        }
    }

    @Inject
    public RegistryTableView(InstanceRegistryCache cache) {
        this.registryCache = cache;
        gson = new GsonBuilder().registerTypeAdapter(InstanceInfo.class, new EurekaRegistryItemTypeAdapter()).create();
    }

    @Override
    public List<String> getColumns() {
        return Column.getAllColumnNames();
    }

    @Override
    public TableViewResource setColumnSearchTerm(String column, String term) {
        return this;
    }

    @Override
    public TableViewResource setAllColumnsSearchTerm(String term) {
        this.searchTerm = term;
        return this;
    }

    @Override
    public TableViewResource enableColumnSort(String column, boolean isDescending) {
        final Column columnSorted = Column.valueOf(column);
        if (currentPage != null) {
            currentPage.sortColumn = columnSorted;
            currentPage.sortDescending = isDescending;
        } else {
            currentPage = new PageInfo(columnSorted, isDescending);
        }
        return this;
    }

    @Override
    public JsonArray getData() {
        final Map<String, InstanceInfo> registryMap = registryCache.get();
        if (registryMap != null) {
            Collection<InstanceInfo> instanceInfoList = registryMap.values();
            totalRecords = instanceInfoList.size();
            instanceInfoList = applyFiltering(instanceInfoList);
            instanceInfoList = applySorting(instanceInfoList);
            instanceInfoList = applyPagination(instanceInfoList);

            final JsonElement jsonElement = gson.toJsonTree(instanceInfoList);
            if (JsonArray.class.isAssignableFrom(jsonElement.getClass())) {
                return (JsonArray) jsonElement;
            }
        }

        return new JsonArray();
    }

    private Collection<InstanceInfo> applySorting(Collection<InstanceInfo> instanceInfoList) {
        if (currentPage != null && currentPage.sortColumn != null) {
            final Comparator<InstanceInfo> instanceInfoComparator = RegistryTableComparatorFactory.makeComparator(currentPage.sortColumn, currentPage.sortDescending);
            if (instanceInfoComparator == null) {
                // no applicable comparator
                return instanceInfoList;
            }

            if (instanceInfoList instanceof List) {
                Collections.sort((List<InstanceInfo>) instanceInfoList, instanceInfoComparator);
                return instanceInfoList;
            } else {
                final ArrayList<InstanceInfo> instanceInfos = Lists.newArrayList(instanceInfoList);
                Collections.sort(instanceInfos, instanceInfoComparator);
                return instanceInfos;
            }
        }

        return instanceInfoList;
    }

    private Collection<InstanceInfo> applyPagination(Collection<InstanceInfo> instanceInfoList) {
        if (currentPage == null || currentPage.count == 0) {
            return instanceInfoList;
        }
        List<InstanceInfo> result = new ArrayList<>(instanceInfoList.size());

        int index = 0;
        int endIndex = currentPage.startIndex + currentPage.count;
        for (InstanceInfo property : instanceInfoList) {
            if (index >= currentPage.startIndex && index < endIndex) {
                result.add(property);
            }

            if (index >= endIndex) {
                // no need to scan further elements
                break;
            }
            index++;
        }
        return result;
    }

    private Collection<InstanceInfo> applyFiltering(Collection<InstanceInfo> instanceInfoList) {
        if (searchTerm != null && !searchTerm.isEmpty()) {
            List<InstanceInfo> result = new ArrayList<>();
            for (InstanceInfo instanceInfo : instanceInfoList) {
                final String app = instanceInfo.getApp();
                if (containsIn(instanceInfo.getApp()) ||
                        containsIn(instanceInfo.getVipAddress()) ||
                        (instanceInfo.getStatus() != null && containsIn(instanceInfo.getStatus().name()))) {
                    result.add(instanceInfo);
                }
            }
            numFilteredRecords = result.size();
            return result;
        } else {
            numFilteredRecords = instanceInfoList.size();
            return instanceInfoList;
        }
    }

    @Override
    public int getTotalNumOfRecords() {
        return totalRecords;
    }

    @Override
    public int getFilteredNumOfRecords() {
        return numFilteredRecords;
    }

    @Override
    public TableViewResource setCurrentPageInfo(int startIndex, int count) {
        if (currentPage != null) {
            currentPage.startIndex = startIndex;
            currentPage.count = count;
        } else {
            currentPage = new PageInfo(startIndex, count);
        }
        return this;
    }

    public static String extractHostname(InstanceInfo instanceInfo) {
        if (AwsDataCenterInfo.class.isAssignableFrom(instanceInfo.getDataCenterInfo().getClass())) {
            final AwsDataCenterInfo dataCenterInfo = (AwsDataCenterInfo) instanceInfo.getDataCenterInfo();
            return dataCenterInfo.getPublicAddress().getHostName();
        }
        return "";
    }

    private boolean containsIn(String target) {
        return target != null &&
                target.toLowerCase().contains(searchTerm.toLowerCase());
    }
}
