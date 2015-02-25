package netflix.adminresources.resources;

/**
 * @author Tomasz Bak
 */
public class GenericSubsystemDescriptor {

    private final String[] statuses;
    private final String className;
    private final String title;
    private final String description;

    /* For serializer */ GenericSubsystemDescriptor() {
        this.statuses = null;
        this.className = null;
        this.title = null;
        this.description = null;
    }

    public GenericSubsystemDescriptor(String[] statuses, String className, String title, String description) {
        this.statuses = statuses;
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

    public String[] getStatuses() {
        return statuses;
    }
}
