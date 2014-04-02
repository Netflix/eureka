package com.netflix.appinfo;

/**
 * Generally indicates the unique identifier of a {@link com.netflix.appinfo.DataCenterInfo}, if applicable.
 *
 * @author rthomas@atlassian.com
 */
public interface UniqueIdentifier {
    String getId();
}
