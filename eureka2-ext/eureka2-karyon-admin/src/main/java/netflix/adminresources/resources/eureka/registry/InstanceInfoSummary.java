package netflix.adminresources.resources.eureka.registry;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import com.netflix.eureka2.registry.instance.NetworkAddress;
import com.netflix.eureka2.registry.instance.NetworkAddress.ProtocolType;
import com.netflix.eureka2.registry.selector.AddressSelector;

/**
 * Representation of an instance info fields rendered on the web admin UI tab.
 *
 * @author Tomasz Bak
 */
public class InstanceInfoSummary {

    private static final AddressSelector IP_ADDRESS_SELECTOR = AddressSelector.selectBy()
            .protocolType(ProtocolType.IPv4).publicIp(true).or().any();

    private final String application;
    private final String instanceId;
    private final Status status;
    private final String vipAddress;
    private final String ipAddress;
    private final String hostName;

    public InstanceInfoSummary(String application, String instanceId, Status status, String vipAddress, String ipAddress, String hostName) {
        this.application = application;
        this.instanceId = instanceId;
        this.status = status;
        this.vipAddress = vipAddress;
        this.ipAddress = ipAddress;
        this.hostName = hostName;
    }

    public InstanceInfoSummary(InstanceInfo instanceInfo) {
        this.application = instanceInfo.getApp();
        this.instanceId = instanceInfo.getId();
        this.status = instanceInfo.getStatus();
        this.vipAddress = instanceInfo.getVipAddress();
        NetworkAddress networkAddress = IP_ADDRESS_SELECTOR.returnAddress(instanceInfo.getDataCenterInfo().getAddresses());
        this.ipAddress = networkAddress.getIpAddress();
        this.hostName = networkAddress.getHostName();
    }

    public String getApplication() {
        return application;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public Status getStatus() {
        return status;
    }

    public String getVipAddress() {
        return vipAddress;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getHostName() {
        return hostName;
    }
}
