package com.netflix.appinfo.providers;

/**
 * This only really exist for legacy support
 */
public interface VipAddressResolver {

    /**
     * Convert <code>VIPAddress</code> by substituting environment variables if necessary.
     *
     * @param vipAddressMacro the macro for which the interpolation needs to be made.
     * @return a string representing the final <code>VIPAddress</code> after substitution.
     */
    String resolveDeploymentContextBasedVipAddresses(String vipAddressMacro);
}
