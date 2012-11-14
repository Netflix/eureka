package com.netflix.logging.messaging;

import java.util.List;

/**
 * An interface for handling the batched messages. The implementors need
 * to define what needs to be done with the batched messages.
 * 
 * @author kranganathan
 *
 */
public interface MessageProcessor<T>
{

	/**
	 * Contract for handling the batched objects.
	 * @param objects - The list of objects that are batched.
	 */
    void process(List<T> objects);
   
}
