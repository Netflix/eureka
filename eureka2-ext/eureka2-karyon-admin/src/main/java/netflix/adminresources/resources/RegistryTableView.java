package netflix.adminresources.resources;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.inject.Inject;
import netflix.adminresources.tableview.TableViewResource;

public class RegistryTableView implements TableViewResource {
    private final Gson gson;
    private String searchTerm;
    private InstanceRegistryCache registryCache;

    private PageInfo currentPage;
    private int totalRecords;
    private int numFilteredRecords;

    public enum Column {
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
        gson = new GsonBuilder().create();
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
        final Map<String, InstanceInfoSummary> registryMap = registryCache.get();
        if (registryMap != null) {
            Collection<InstanceInfoSummary> instanceInfoList = registryMap.values();
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

    private Collection<InstanceInfoSummary> applySorting(Collection<InstanceInfoSummary> instanceInfoList) {
        if (currentPage != null && currentPage.sortColumn != null) {
            final Comparator<InstanceInfoSummary> instanceInfoComparator =
                    RegistryTableComparatorFactory.makeComparator(currentPage.sortColumn, currentPage.sortDescending);
            if (instanceInfoComparator == null) {
                // no applicable comparator
                return instanceInfoList;
            }

            if (instanceInfoList instanceof List) {
                Collections.sort((List<InstanceInfoSummary>) instanceInfoList, instanceInfoComparator);
                return instanceInfoList;
            } else {
                final ArrayList<InstanceInfoSummary> instanceInfos = Lists.newArrayList(instanceInfoList);
                Collections.sort(instanceInfos, instanceInfoComparator);
                return instanceInfos;
            }
        }

        return instanceInfoList;
    }

    private Collection<InstanceInfoSummary> applyPagination(Collection<InstanceInfoSummary> instanceInfoList) {
        if (currentPage == null || currentPage.count == 0) {
            return instanceInfoList;
        }
        List<InstanceInfoSummary> result = new ArrayList<>(instanceInfoList.size());

        int index = 0;
        int endIndex = currentPage.startIndex + currentPage.count;
        for (InstanceInfoSummary property : instanceInfoList) {
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

    private Collection<InstanceInfoSummary> applyFiltering(Collection<InstanceInfoSummary> instanceInfoList) {
        if (searchTerm != null && !searchTerm.isEmpty()) {
            List<InstanceInfoSummary> result = new ArrayList<>();
            for (InstanceInfoSummary instanceInfo : instanceInfoList) {
                final String app = instanceInfo.getApplication();
                if (containsIn(instanceInfo.getApplication()) ||
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

    private boolean containsIn(String target) {
        return target != null &&
                target.toLowerCase().contains(searchTerm.toLowerCase());
    }
}
