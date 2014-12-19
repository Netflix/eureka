package netflix.admin.eureka;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import netflix.adminresources.tableview.DataTableHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.*;

@Path("/webadmin/eureka2")
@Produces(MediaType.APPLICATION_JSON)
public class EurekaResource {
    private static final Logger logger = LoggerFactory.getLogger(EurekaResource.class);

    @Inject(optional = true)
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
