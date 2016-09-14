package com.netflix.appinfo;

/**
 * @author David Liu
 */
final class PropertyBasedAmazonInfoConfigConstants {

    static final String LOG_METADATA_ERROR_KEY = "logAmazonMetadataErrors";
    static final String READ_TIMEOUT_KEY = "mt.read_timeout";
    static final String CONNECT_TIMEOUT_KEY = "mt.connect_timeout";
    static final String NUM_RETRIES_KEY = "mt.num_retries";
    static final String FAIL_FAST_ON_FIRST_LOAD_KEY = "mt.fail_fast_on_first_load";

    static final String SHOULD_VALIDATE_INSTANCE_ID_KEY = "validateInstanceId";


    static class Values {
        static final int DEFAULT_READ_TIMEOUT = 5000;
        static final int DEFAULT_CONNECT_TIMEOUT = 2000;
        static final int DEFAULT_NUM_RETRIES = 3;
    }
}
