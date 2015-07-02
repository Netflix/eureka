package netflix.adminresources.resources.eureka.status;

/**
 * @author Tomasz Bak
 */
public class GenericSubsystemDescriptor {

    private final String className;
    private final String title;
    private final String description;

    /* For serializer */ GenericSubsystemDescriptor() {
        this.className = null;
        this.title = null;
        this.description = null;
    }

    public GenericSubsystemDescriptor(String className, String title, String description) {
        this.className = className;
        this.title = title;
        this.description = description;
    }

    public String getClassName() {
        return className;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
