/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka.datastore;

import rx.Observable;

/**
 * An abstract datastore for uniquely identifiable and indexable items
 * @param <E> the data type stored in this store
 *
 * @author David Liu
 */
public abstract class Store<E extends Item> {

    /**
     * @param id the id of the item to check
     * @return an observable of the result, true if the item exist, false if not
     */
    public abstract Observable<Boolean> contains(String id);

    /**
     * @return an observable that emits the single item with the given id.
     */
    public abstract Observable<E> get(String id);

    /**
     * Add item to the store if not already exist
     * @param item the item to be added
     * @return true for a successful add, false otherwise
     */
    protected abstract Observable<Boolean> add(E item);

    /**
     * Add the item to the store if not exist, otherwise overwrite the existing item
     * @param item the item to add or set in the store
     */
    protected abstract Observable<Void> set(E item);

    /**
     * @param id the id of the item to remove
     * @return an observable of the removed element, result may be null
     */
    protected abstract Observable<E> remove(String id);
}
