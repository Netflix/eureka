package com.netflix.eureka.util.batcher;

import java.util.List;

/**
 * This class is taken from blitz4j.
 * <p>
 * An interface for handling the batched messages. The implementers need to
 * define what needs to be done with the batched messages.
 *
 * @author Karthik Ranganathan
 *
 */
public interface MessageProcessor<T> {

    /**
     * Contract for handling the batched objects.
     *
     * @param objects
     *            - The list of objects that are batched.
     */
    void process(List<T> objects);

}
