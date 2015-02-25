package netflix.adminresources.resources;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

/**
 * @author Tomasz Bak
 */
public class AggregatedStatusTableView extends StatusTableView {

    @Inject
    public AggregatedStatusTableView(StatusRegistry statusRegistry) {
        super(statusRegistry);
    }

    @Override
    public JsonArray getData() {
        if (statusRegistry.getAggregated() != null) {
            List<GenericHealthStatusUpdate> aggregated = Collections.singletonList(statusRegistry.getAggregated());
            final JsonElement jsonElement = gson.toJsonTree(aggregated);
            if (JsonArray.class.isAssignableFrom(jsonElement.getClass())) {
                return (JsonArray) jsonElement;
            }
        }

        return new JsonArray();
    }

    @Override
    public int getTotalNumOfRecords() {
        return 1;
    }

    @Override
    public int getFilteredNumOfRecords() {
        return 1;
    }

}
