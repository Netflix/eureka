package com.netflix.eureka2.metric.noop;

import com.netflix.eureka2.metric.StateMachineMetrics;

/**
 * @author Tomasz Bak
 */
public class NoOpStateMachineMetrics<STATE extends Enum<STATE>> implements StateMachineMetrics<STATE> {
    @Override
    public void incrementStateCounter(STATE state) {
    }

    @Override
    public void stateTransition(STATE from, STATE to) {
    }

    @Override
    public void decrementStateCounter(STATE state) {
    }
}
