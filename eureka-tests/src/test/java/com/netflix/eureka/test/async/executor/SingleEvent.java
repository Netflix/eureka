package com.netflix.eureka.test.async.executor;

import com.google.common.base.Preconditions;

/**
 * Single event which represents the action that will be executed in a time line.
 */
public class SingleEvent {

    /**
     * Interval time between the previous event.
     */
    private int intervalTimeInMs;

    /**
     * Action to take.
     */
    private Action action;

    /**
     * Constructor.
     */
    public SingleEvent(int intervalTimeInMs, Action action) {
        this.intervalTimeInMs = intervalTimeInMs;
        this.action = action;
    }

    public int getIntervalTimeInMs() {
        return intervalTimeInMs;
    }

    public Action getAction() {
        return action;
    }

    /**
     * SingleEvent builder.
     */
    public static final class Builder {

        /**
         * Interval time between the previous event.
         */
        private int intervalTimeInMs;

        /**
         * Action to take.
         */
        private Action action;

        private Builder() {
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        public Builder withIntervalTimeInMs(int intervalTimeInMs) {
            this.intervalTimeInMs = intervalTimeInMs;
            return this;
        }

        public Builder withAction(Action action) {
            this.action = action;
            return this;
        }

        public SingleEvent build() {
            Preconditions.checkNotNull(intervalTimeInMs, "IntervalTimeInMs is not set for SingleEvent");
            Preconditions.checkNotNull(action, "Action is not set for SingleEvent");
            return new SingleEvent(intervalTimeInMs, action);
        }
    }

    @Override
    public String toString() {
        return "SingleEvent{" +
            "intervalTimeInMs=" + intervalTimeInMs +
            ", action=" + action +
            '}';
    }

    /**
     * Action to perform.
     */
    public interface Action {
        void execute();
    }

}
