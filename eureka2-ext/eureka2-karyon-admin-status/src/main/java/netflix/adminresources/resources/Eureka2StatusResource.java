package netflix.adminresources.resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import netflix.adminresources.tableview.DataTableHelper;
import netflix.adminresources.tableview.TableViewResource;

/**
 * @author Tomasz Bak
 */
@Path("/eurekaStatus")
@Produces(MediaType.APPLICATION_JSON)
public class Eureka2StatusResource {

    @Inject(optional = false)
    private StatusTableView statusTableView;

    @Inject(optional = false)
    private AggregatedStatusTableView aggregatedTableView;

    @GET
    public Response getComponentStatuses(@Context UriInfo uriInfo) {
        return statusListFor(uriInfo, statusTableView);
    }

    @GET
    @Path("/aggregated")
    public Response getAggregatedStatus(@Context UriInfo uriInfo) {
        return statusListFor(uriInfo, aggregatedTableView);
    }

    private static Response statusListFor(UriInfo uriInfo, TableViewResource tableView) {
        if (tableView != null) {
            MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
            JsonObject output = DataTableHelper.buildOutput(tableView, queryParams);
            return Response.ok().entity(new Gson().toJson(output)).build();
        } else {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }
}
