package com.netflix.eureka.test.async.executor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;

/**
 * SequentialEvents represents multiple events which should be executed in a
 * sequential manner.
 */
public class SequentialEvents {

    /**
     * Multiple single events.
     */
    private List<SingleEvent> eventList;

    /**
     * Default constructor.
     *
     * @param eventList event list.
     */
    private SequentialEvents(List<SingleEvent> eventList) {
        this.eventList = eventList;
    }

    /**
     * Instance creator.
     *
     * @return SequentialEvents.
     */
    public static SequentialEvents newInstance() {
        return new SequentialEvents(new ArrayList<>());
    }

    /**
     * Instance creator with expected event list size.
     *
     * @param expectedEventListSize expected event list size.
     * @return SequentialEvents.
     */
    public static SequentialEvents newInstance(int expectedEventListSize) {
        return new SequentialEvents(new ArrayList<>(expectedEventListSize));
    }

    /**
     * Build sequential events from multiple single events.
     *
     * @param event a bunch of single events.
     * @return SequentialEvents.
     */
    public static SequentialEvents of(SingleEvent... events) {
        return new SequentialEvents(Arrays.asList(events));
    }

    /**
     * Build sequential events from multiple single events.
     *
     * @param eventList a bunch of single events.
     * @return SequentialEvents.
     */
    public static SequentialEvents of(List<SingleEvent> eventList) {
        return new SequentialEvents(eventList);
    }

    /**
     * Add one more single event to sequential events.
     *
     * @param event one event.
     * @return SequentialEvents.
     */
    public SequentialEvents with(SingleEvent event) {
        Preconditions.checkNotNull(eventList, "eventList should not be null");
        this.eventList.add(event);
        return this;
    }

    /**
     * Get event lists.
     *
     * @return event lists.
     */
    public List<SingleEvent> getEventList() {
        return eventList;
    }

    @Override
    public String toString() {
        return "SequentialEvents{" +
            "eventList=" + eventList +
            '}';
    }
}
