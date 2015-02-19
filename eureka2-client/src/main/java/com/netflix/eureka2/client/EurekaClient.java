package com.netflix.eureka2.client;

import com.netflix.eureka2.client.interest.EurekaInterestClient;
import com.netflix.eureka2.client.registration.EurekaRegistrationClient;

/**
 * FIXME delete once we decide on the strategy to construct the two individual clients
 *
 * @author David Liu
 */
public interface EurekaClient extends EurekaInterestClient, EurekaRegistrationClient {
}
