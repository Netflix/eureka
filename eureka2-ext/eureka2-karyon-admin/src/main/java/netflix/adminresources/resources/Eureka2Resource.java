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

@Path("/eureka2")
@Produces(MediaType.APPLICATION_JSON)
public class Eureka2Resource {

    @Inject(optional = false)
    private RegistryTableView registryTableView;

    @GET
    public Response getProperties(@Context UriInfo uriInfo) {
        if (registryTableView != null) {
            MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
            JsonObject output = DataTableHelper.buildOutput(registryTableView, queryParams);
            return Response.ok().entity(new Gson().toJson(output)).build();
        } else {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

}
