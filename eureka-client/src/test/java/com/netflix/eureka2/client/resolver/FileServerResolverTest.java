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

package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.client.resolver.ServerResolver.Server;
import com.netflix.eureka2.rx.RxBlocking;
import com.netflix.eureka2.utils.Sets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * @author Tomasz Bak
 */
public class FileServerResolverTest {

    private File configurationFile;
    private FileServerResolver resolver;
    private TestScheduler testScheduler;

    @Before
    public void setUp() throws Exception {
        configurationFile = File.createTempFile("eureka-resolver-test", ".conf");
        updateFile("serverA", "serverB");

        // We need to force reload, as file last update time resolution is 1sec. Too long to wait.
        testScheduler = Schedulers.test();
        resolver = new FileServerResolver(configurationFile, 10, 100, TimeUnit.MILLISECONDS, true, testScheduler);
    }

    @After
    public void tearDown() throws Exception {
        if (configurationFile != null && configurationFile.exists()) {
            configurationFile.delete();
        }
    }

    @Test
    public void testReadingServersFromFile() throws Exception {
        Iterator<Server> serverEntryIterator = RxBlocking.iteratorFrom(30, TimeUnit.SECONDS, resolver.resolve());

        Set<ServerResolver.Server> expected = Sets.asSet(new ServerResolver.Server("serverA", 0),
                                                         new Server("serverB", 0));

        assertTrue(expected.contains(serverEntryIterator.next()));
        assertTrue(expected.contains(serverEntryIterator.next()));

        // Now update the file, and change one server address
        updateFile("serverA", "serverC");

        testScheduler.advanceTimeBy(10, TimeUnit.MILLISECONDS);

        expected = Sets.asSet(new ServerResolver.Server("serverA", 0), new Server("serverC", 0));

        serverEntryIterator = RxBlocking.iteratorFrom(30, TimeUnit.SECONDS, resolver.resolve());
        assertTrue(expected.contains(serverEntryIterator.next()));
        assertTrue(expected.contains(serverEntryIterator.next()));
    }

    private void updateFile(String... servers) throws IOException {
        configurationFile.delete();
        try (FileWriter writer = new FileWriter(configurationFile)) {
            for (String server : servers) {
                writer.write(server);
                writer.write('\n');
            }
        }
    }
}