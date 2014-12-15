package netflix.admin.eureka;

import com.google.gson.*;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.datacenter.AwsDataCenterInfo;

import java.lang.reflect.Type;

public class EurekaRegistryItemTypeAdapter implements JsonSerializer<InstanceInfo> {
    @Override
    public JsonElement serialize(InstanceInfo instanceInfo, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        if (AwsDataCenterInfo.class.isAssignableFrom(instanceInfo.getDataCenterInfo().getClass())) {
            final AwsDataCenterInfo dataCenterInfo = (AwsDataCenterInfo) instanceInfo.getDataCenterInfo();
            result.addProperty("instId", dataCenterInfo.getInstanceId());
            result.addProperty("ip", dataCenterInfo.getPublicAddress().getIpAddress());
            result.addProperty("hostname", dataCenterInfo.getPublicAddress().getHostName());
            result.addProperty("zone", dataCenterInfo.getZone());
            result.addProperty("reg", dataCenterInfo.getRegion());
        }
        result.addProperty("appId", instanceInfo.getApp());
        result.addProperty("status", instanceInfo.getStatus().name());
        result.addProperty("vip", instanceInfo.getVipAddress());
        result.addProperty("ts", instanceInfo.getVersion());
        return result;
    }
}
