package netflix.admin.eureka;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.functions.Action1;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Path("/webadmin/eureka2")
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class EurekaResource {
    private static final Logger logger = LoggerFactory.getLogger(EurekaResource.class);
    private final Gson gson;
    private final Map<String, InstanceInfo> registryCache;
    private final EurekaClient eurekaClient;

    @Inject
    public EurekaResource() {
        ServerResolver serverResolver = ServerResolvers.just("ec2-107-20-175-144.compute-1.amazonaws.com", 12103);
        eurekaClient = Eureka.newClient(serverResolver);
        registryCache = new ConcurrentHashMap<>();
        gson = new GsonBuilder().registerTypeAdapter(InstanceInfo.class, new EurekaRegistryItemTypeAdapter()).create();
        start();
    }

    private void start() {
        eurekaClient.forInterest(Interests.forFullRegistry()).retry().subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
            @Override
            public void call(ChangeNotification<InstanceInfo> changeNotification) {
                final ChangeNotification.Kind notificationKind = changeNotification.getKind();
                final InstanceInfo instInfo = changeNotification.getData();
                if (notificationKind == ChangeNotification.Kind.Add ||
                        notificationKind == ChangeNotification.Kind.Modify) {
                    registryCache.put(instInfo.getId(), instInfo);
                } else if (notificationKind == ChangeNotification.Kind.Delete) {
                    registryCache.remove(instInfo.getId());
                }
            }
        });
    }

    @GET
    public Response getEurekaRegistry() {
        try {
            return Response.ok().entity(serializeCache()).build();
        } catch (Exception ex) {
            logger.error("Exception in fetching eureka registry ", ex);
            return Response.status(Response.Status.BAD_REQUEST.getStatusCode()).build();
        }
    }

    private String serializeCache() {
        return gson.toJson(registryCache.values());
    }

}
