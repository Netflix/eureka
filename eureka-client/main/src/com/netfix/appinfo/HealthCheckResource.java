package com.netflix.appinfo;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.netflix.logging.ILog;
import com.netflix.logging.LogManager;

/**
 * Default health check resource that comes out of the box with platform.
 * 
 * Required /healthcheck url for applications running in the cloud:
 *   http://wiki.netflix.com/clearspace/docs/DOC-20541
 *   HTTP Response codes:
 *      200                 => Up and healthy
 *      204                 => Starting
 *      (anything else)     => Down
 */
@Path("/healthcheck")
public class HealthCheckResource {
	private static final ILog s_logger = LogManager.getLogger(HealthCheckResource.class);

	@GET
	public Response doHealthCheck() {
	    try {
	        InstanceInfo myInfo = ApplicationInfoManager.getInstance().getInfo();

	        switch(myInfo.getStatus()) {
	        case UP:
	            //Return status 200
                return Response.status(Status.OK).build();
	        case STARTING: 
	            //Return status 204
	            return Response.status(Status.NO_CONTENT).build();
	        case OUT_OF_SERVICE:
	            //Return 503
	            return Response.status(Status.SERVICE_UNAVAILABLE).build();
	        default:
	            //Return status 500
                return Response.status(Status.INTERNAL_SERVER_ERROR).build();
	        }
	    } catch (Throwable th) {
	        s_logger.error(th);
            //Return status 500
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
	    }
	}
}