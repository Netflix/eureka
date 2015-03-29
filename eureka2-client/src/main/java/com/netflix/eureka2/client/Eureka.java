package com.netflix.eureka2.client;

/**
* Entry point class for eureka2 clients
*
* @author David Liu
*/
public final class Eureka {

    private static final InterestFunctions INTEREST_FUNCTIONS = new InterestFunctions();

    /**
     * Return a builder for creating eureka clients to read interest from remote eureka servers.
     *
     * @return {@link EurekaInterestClientBuilder}
     */
    public static EurekaInterestClientBuilder newInterestClientBuilder() {
        return new EurekaInterestClientBuilder();
    }

    /**
     * Return a builder for creating eureka clients to register instanceInfos with remote eureka servers.
     *
     * @return {@link EurekaRegistrationClientBuilder}
     */
    public static EurekaRegistrationClientBuilder newRegistrationClientBuilder() {
        return new EurekaRegistrationClientBuilder();
    }

    /**
     * Return an entry point class for various useful functions that can be applied to eureka interest streams.
     *
     * @return {@link InterestFunctions}
     */
    public static InterestFunctions interestFunctions() {
        return INTEREST_FUNCTIONS;
    }
}
