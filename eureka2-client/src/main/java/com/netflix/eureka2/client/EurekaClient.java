package com.netflix.eureka2.client;

import com.netflix.eureka2.client.interest.EurekaInterestClient;
import com.netflix.eureka2.client.registration.EurekaRegistrationClient;

/**
 * @author David Liu
 */
public interface EurekaClient extends EurekaInterestClient, EurekaRegistrationClient {
}
