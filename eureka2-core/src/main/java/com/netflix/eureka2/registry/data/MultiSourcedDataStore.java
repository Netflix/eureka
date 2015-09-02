package com.netflix.eureka2.registry.data;

import com.netflix.eureka2.EurekaCloseable;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.Source;

import java.util.Collection;

/**
 * @author David Liu
 */
public interface MultiSourcedDataStore<T> extends EurekaCloseable {

    ChangeNotification<T>[] update(T data, Source source);

    ChangeNotification<T>[] remove(String dataId, Source source);

    Collection<MultiSourcedDataHolder<T>> values();

    MultiSourcedDataHolder<T> get(String id);

    int size();
}
