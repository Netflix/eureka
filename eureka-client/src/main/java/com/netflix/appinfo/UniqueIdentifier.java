package com.netflix.appinfo;

import javax.annotation.Nullable;

/**
 * Generally indicates the unique identifier of a {@link com.netflix.appinfo.DataCenterInfo}, if applicable.
 *
 * @author rthomas@atlassian.com
 */
public interface UniqueIdentifier {
    @Nullable String getId();
}
