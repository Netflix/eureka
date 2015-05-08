package com.netflix.eureka2.performance;

import rx.Observable;

/**
 * @author Tomasz Bak
 */
public abstract class ClientActor implements Comparable<ClientActor> {

    private final String name;

    protected ClientActor(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    protected abstract Observable<Void> lifecycle();

    public abstract void stop();

    @Override
    public int compareTo(ClientActor other) {
        if (this == other) {
            return 0;
        }
        return name.compareTo(other.name);
    }
}
