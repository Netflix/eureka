package netflix.adminresources.resources;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import netflix.adminresources.tableview.TableViewResource;

/**
 * @author Tomasz Bak
 */
public class StatusTableView implements TableViewResource {

    public enum Column {
        Status,
        Title,
        Statuses,
        Description;

        public static List<String> getAllColumnNames() {
            List<String> columns = new ArrayList<String>();
            for (Column col : Column.values()) {
                columns.add(col.name());
            }
            return columns;
        }
    }

    protected final Gson gson;
    protected final StatusRegistry statusRegistry;

    @Inject
    public StatusTableView(StatusRegistry statusRegistry) {
        this.statusRegistry = statusRegistry;
        this.gson = new GsonBuilder().create();
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
        return this;
    }

    @Override
    public TableViewResource enableColumnSort(String column, boolean isDescending) {
        return this;
    }

    @Override
    public JsonArray getData() {
        List<GenericHealthStatusUpdate> genericHealthStatusUpdates = statusRegistry.get();
        if (!genericHealthStatusUpdates.isEmpty()) {
            final JsonElement jsonElement = gson.toJsonTree(genericHealthStatusUpdates);
            if (JsonArray.class.isAssignableFrom(jsonElement.getClass())) {
                return (JsonArray) jsonElement;
            }
        }

        return new JsonArray();
    }

    @Override
    public int getTotalNumOfRecords() {
        return statusRegistry.size();
    }

    @Override
    public int getFilteredNumOfRecords() {
        return statusRegistry.size();
    }

    @Override
    public TableViewResource setCurrentPageInfo(int startIndex, int count) {
        return this;
    }
}
