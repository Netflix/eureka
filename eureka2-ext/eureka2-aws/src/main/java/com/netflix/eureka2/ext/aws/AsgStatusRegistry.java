package com.netflix.eureka2.ext.aws;

import com.google.inject.ImplementedBy;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
@ImplementedBy(AsgStatusRegistryImpl.class)
public interface AsgStatusRegistry {
    Observable<Boolean> asgStatusUpdates(String asg);
}
