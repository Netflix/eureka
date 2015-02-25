package netflix.adminresources.resources;

/**
 * @author Tomasz Bak
 */
public class GenericHealthStatusUpdate {
    private final String status;
    private final GenericSubsystemDescriptor descriptor;

    /* For serializer */ GenericHealthStatusUpdate() {
        this.status = null;
        this.descriptor = null;
    }

    public GenericHealthStatusUpdate(String status, GenericSubsystemDescriptor descriptor) {
        this.status = status;
        this.descriptor = descriptor;
    }

    public String getStatus() {
        return status;
    }

    public GenericSubsystemDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public String toString() {
        return "HealthStatusItem{" +
                "status='" + status + '\'' +
                ", descriptor=" + descriptor +
                '}';
    }
}
