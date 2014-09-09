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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka.client.ServerResolver.ServerEntry;
import com.netflix.eureka.client.ServerResolver.ServerEntry.Action;
import com.netflix.eureka.rx.RxBlocking;
import com.netflix.eureka.utils.Sets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.schedulers.Schedulers;

import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class FileServerResolverTest {

    private File configurationFile;
    private FileServerResolver resolver;

    @Before
    public void setUp() throws Exception {
        configurationFile = File.createTempFile("eureka-resolver-test", ".conf");
        updateFile("serverA", "serverB");

        // We need to force reload, as file last update time resolution is 1sec. Too long to wait.
        resolver = new FileServerResolver(configurationFile, true, 10, TimeUnit.MILLISECONDS, Schedulers.io()) {
            @Override
            protected ResolverTask createResolveTask() {
                return new FileResolveTask() {
                    @Override
                    boolean isUpdated() {
                        return true;
                    }
                };
            }
        };
        resolver.start();
    }

    @After
    public void tearDown() throws Exception {
        if (configurationFile != null && configurationFile.exists()) {
            configurationFile.delete();
        }

        if (resolver != null) {
            resolver.close();
        }
    }

    @Test
    public void testReadingServersFromFile() throws Exception {
        Iterator<ServerEntry<InetSocketAddress>> serverEntryIterator = RxBlocking.iteratorFrom(30, TimeUnit.SECONDS, resolver.resolve());
        Set<ServerEntry<InetSocketAddress>> expected = Sets.asSet(
                new ServerEntry<InetSocketAddress>(Action.Add, new InetSocketAddress("serverA", 0)),
                new ServerEntry<InetSocketAddress>(Action.Add, new InetSocketAddress("serverB", 0))
        );
        assertTrue(expected.contains(serverEntryIterator.next()));
        assertTrue(expected.contains(serverEntryIterator.next()));

        // Now update the file, and change one server address
        updateFile("serverA", "serverC");

        assertEquals(new ServerEntry<InetSocketAddress>(Action.Remove, new InetSocketAddress("serverB", 0)), serverEntryIterator.next());
        assertEquals(new ServerEntry<InetSocketAddress>(Action.Add, new InetSocketAddress("serverC", 0)), serverEntryIterator.next());
    }

    private void updateFile(String... servers) throws IOException {
        configurationFile.delete();
        FileWriter writer = new FileWriter(configurationFile);
        try {
            for (String server : servers) {
                writer.write(server);
                writer.write('\n');
            }
        } finally {
            writer.close();
        }
    }
}