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

package com.netflix.eureka.client.bootstrap;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.client.ServerResolver.ServerEntry.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.functions.Action0;
import rx.subjects.PublishSubject;

/**
 * Abstract class for resolvers with dynamic server list sources.
 *
 * @author Tomasz Bak
 */
public abstract class AbstractServerResolver<A extends SocketAddress> implements ServerResolver<A> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractServerResolver.class);

    private final boolean refresh;
    protected final Worker schedulerWorker;

    private final PublishSubject<ServerEntry<A>> updateSubject = PublishSubject.create();

    /**
     * Guards access to the {@link #serverAddEntries}, to make
     * server list update, and a new subscription mutally exclusive operations.
     */
    private final Lock lock = new ReentrantLock();
    private Set<ServerEntry<A>> serverAddEntries;

    protected AbstractServerResolver(boolean refresh, Scheduler scheduler) {
        this.refresh = refresh;
        this.schedulerWorker = scheduler.createWorker();
    }

    @Override
    public void start() {
        scheduleLookup(createResolveTask(), 0, TimeUnit.SECONDS, true);
    }

    protected abstract ResolverTask createResolveTask();

    protected abstract IOException noServersFound(Exception ex);

    @Override
    public Observable<ServerEntry<A>> resolve() {
        return Observable.create(new OnSubscribe<ServerEntry<A>>() {
            @Override
            public void call(Subscriber<? super ServerEntry<A>> subscriber) {
                lock.lock();
                try {
                    Observable<ServerEntry<A>> observable;
                    if (serverAddEntries != null) {
                        observable = Observable.concat(Observable.from(serverAddEntries), updateSubject);
                    } else {
                        observable = updateSubject;
                    }
                    observable.subscribe(subscriber);
                } finally {
                    lock.unlock();
                }
            }
        });
    }

    @Override
    public void close() {
        if (!schedulerWorker.isUnsubscribed()) {
            schedulerWorker.unsubscribe();
        }
    }

    protected boolean onServerListUpdate(Set<ServerEntry<A>> newAddEntries) {
        if (newAddEntries.isEmpty()) {
            if (serverAddEntries == null) {
                updateSubject.onError(noServersFound(null));
                return false;
            } else {
                if (logger.isWarnEnabled()) {
                    IOException exception = noServersFound(null);
                    logger.warn("Server list refresh failed; keeping the previous one", exception);
                }
                return true;
            }
        }
        lock.lock();
        try {
            if (serverAddEntries == null) {
                for (ServerEntry<A> entry : newAddEntries) {
                    updateSubject.onNext(entry);
                }
            } else {
                for (ServerEntry<A> oldEntry : serverAddEntries) {
                    if (!newAddEntries.contains(oldEntry)) {
                        updateSubject.onNext(new ServerEntry<A>(Action.Remove, oldEntry.getServer(), oldEntry.getProtocol()));
                    }
                }
                for (ServerEntry<A> newEntry : newAddEntries) {
                    if (!serverAddEntries.contains(newEntry)) {
                        updateSubject.onNext(newEntry);
                    }
                }
            }
            serverAddEntries = newAddEntries;
        } finally {
            lock.unlock();
        }
        return true;
    }

    protected boolean onUpdateError(Exception ex) {
        if (serverAddEntries == null) {
            updateSubject.onError(noServersFound(ex));
            return false;
        }
        if (logger.isWarnEnabled()) {
            IOException exception = noServersFound(ex);
            logger.warn("Server list refresh failed; keeping the previous one", exception);
        }
        return true;
    }

    protected void scheduleLookup(ResolverTask resolveTask, long delay, TimeUnit timeUnit) {
        scheduleLookup(resolveTask, delay, timeUnit, false);
    }

    private void scheduleLookup(ResolverTask resolveTask, long delay, TimeUnit timeUnit, boolean first) {
        if (first || refresh) {
            schedulerWorker.schedule(resolveTask, delay, timeUnit);
        }
    }

    public interface ResolverTask extends Action0 {
    }
}
