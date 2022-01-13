package com.netflix.appinfo;

/**
 * Config related to loading of amazon metadata from the EC2 metadata url.
 *
 * @author David Liu
 */
public interface AmazonInfoConfig {

    /**
     * @return the config namespace
     */
    String getNamespace();

    /**
     * @return whether errors reading from the ec2 metadata url should be logged
     */
    boolean shouldLogAmazonMetadataErrors();

    /**
     * @return the read timeout when connecting to the metadata url
     */
    int getReadTimeout();

    /**
     * @return the connect timeout when connecting to the metadata url
     */
    int getConnectTimeout();

    /**
     * @return the number of retries when unable to read a value from the metadata url
     */
    int getNumRetries();

    /**
     * When creating an AmazonInfo via {@link com.netflix.appinfo.AmazonInfo.Builder#autoBuild(String)},
     * a fail fast mechanism exist based on the below configuration.
     * If enabled (default to true), the {@link com.netflix.appinfo.AmazonInfo.Builder#autoBuild(String)}
     * method will exit early after failing to load the value for the first metadata key (instanceId),
     * after the expected number of retries as defined by {@link #getNumRetries()}.
     *
     * @return whether autoloading should fail fast if loading has failed for the first field (after all retries)
     */
    boolean shouldFailFastOnFirstLoad();

    /**
     * When AmazonInfo is specified, setting this to false allows progress on building the AmazonInfo even if
     * the instanceId of the environment is not able to be validated.
     *
     * @return whether to progress with AmazonInfo construction if the instanceId cannot be validated.
     */
    boolean shouldValidateInstanceId();

}
