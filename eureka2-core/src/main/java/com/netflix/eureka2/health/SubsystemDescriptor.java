package com.netflix.eureka2.health;

/**
 * A description of Eureka component/subsystem that has own health status lifecycle.
 * It is provided for logging and UI integration purposes. Single instance of this
 * class shall be created for each subsystem.
 *
 * @author Tomasz Bak
 */
public class SubsystemDescriptor<SUBSYSTEM> {

    private final Class<SUBSYSTEM> subsystemClass;
    private final String title;
    private final String description;

    public SubsystemDescriptor(Class<SUBSYSTEM> subsystemClass, String title, String description) {
        this.subsystemClass = subsystemClass;
        this.title = title;
        this.description = description;
    }

    public Class<SUBSYSTEM> getSubsystemClass() {
        return subsystemClass;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "SubsystemDescriptor{" +
                "subsystemClass=" + subsystemClass +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
