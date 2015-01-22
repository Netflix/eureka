package com.netflix.eureka2.metric;

/**
 * @author Tomasz Bak
 */
public interface StateMachineMetrics<STATE extends Enum<STATE>> {
    void incrementStateCounter(STATE state);

    void stateTransition(STATE from, STATE to);

    void decrementStateCounter(STATE state);
}
