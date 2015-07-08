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

package com.netflix.eureka2.testkit.cli;

import java.util.Map;

import com.netflix.eureka2.testkit.cli.bootstrap.ClusterResolver;
import com.netflix.eureka2.utils.ConfigProxyUtils;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.subjects.BehaviorSubject;

/**
 * @author Tomasz Bak
 */
public class Context {

    private ClusterResolver clusterResolver;
    private BehaviorSubject<ClusterTopology> clusterTopologySubject = BehaviorSubject.create();

    private Session activeSession;
    private volatile ClusterTopology clusterTopology;

    public Observable<ClusterTopology> clusterTopologies() {
        return clusterTopologySubject;
    }

    public Session getActiveSession() {
        return activeSession;
    }

    public void setActiveSession(Session activeSession) {
        this.activeSession = activeSession;
    }

    public boolean hasClusterResolver() {
        return clusterResolver != null;
    }

    public void close() {
        if (activeSession != null) {
            activeSession.close();
            activeSession = null;
        }
    }

    public void setClusterResolver(final ClusterResolver clusterResolver) {
        this.clusterResolver = clusterResolver;
        clusterResolver.connect()
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable e) {
                        System.out.printf("ERROR: cluster topology updates for %s completed with error\n", clusterResolver.getBootstrapAddress());
                        e.printStackTrace();
                    }
                })
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        System.out.printf("Cluster topology updates for %s completed\n", clusterResolver.getBootstrapAddress());
                    }
                })
                .subscribe(clusterTopologySubject);
        clusterTopologySubject.doOnNext(new Action1<ClusterTopology>() {
            @Override
            public void call(ClusterTopology newTopology) {
                clusterTopology = newTopology;
            }
        }).subscribe();
    }

    public void printStatus() {
        System.out.println("Status report");
        System.out.println("=================================================");
        System.out.println("Bootstrap:");
        if(clusterResolver == null) {
            System.out.println("    <not available>");
        } else {
            System.out.println("    server type         " + clusterResolver.getServerType());
            System.out.println("    address             " + clusterResolver.getBootstrapAddress());
            System.out.println("    read cluster VIP    " + clusterResolver.getReadClusterVip());
            if (clusterResolver.getTransportConfig() != null) {
                System.out.println("Transport configuration:");
                for (Map.Entry<String, Object> entry : ConfigProxyUtils.asMap(clusterResolver.getTransportConfig()).entrySet()) {
                    System.out.printf("    %-15s%s", entry.getKey(), entry.getValue());
                }
            }
        }
        System.out.println("Cluster topology:");
        if (clusterTopology == null) {
            System.out.println("    <not available>");
        } else {

            clusterTopology.printFormatted(System.out, 4);
        }
        System.out.println("Active session:");
        if (activeSession == null) {
            System.out.println("    <not available>");
        } else {
            activeSession.printFormatted(System.out, 4);
        }
    }
}
