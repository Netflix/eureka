/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.eureka.util.batcher;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka.util.batcher.TaskProcessor.ProcessingResult;
import org.junit.After;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Tomasz Bak
 */
public class TaskDispatchersTest {

    private static final long SERVER_UNAVAILABLE_SLEEP_TIME_MS = 1000;
    private static final long RETRY_SLEEP_TIME_MS = 100;
    private static final long MAX_BATCHING_DELAY_MS = 10;

    private static final int MAX_BUFFER_SIZE = 1000000;
    private static final int WORK_LOAD_SIZE = 2;

    private final RecordingProcessor processor = new RecordingProcessor();

    private TaskDispatcher<Integer, ProcessingResult> dispatcher;

    @After
    public void tearDown() throws Exception {
        if (dispatcher != null) {
            dispatcher.shutdown();
        }
    }

    @Test
    public void testSingleTaskDispatcher() throws Exception {
        dispatcher = TaskDispatchers.createNonBatchingTaskDispatcher(
                "TEST",
                MAX_BUFFER_SIZE,
                1,
                MAX_BATCHING_DELAY_MS,
                SERVER_UNAVAILABLE_SLEEP_TIME_MS,
                RETRY_SLEEP_TIME_MS,
                processor
        );

        dispatcher.process(1, ProcessingResult.Success, System.currentTimeMillis() + 60 * 1000);
        ProcessingResult result = processor.completedTasks.poll(5, TimeUnit.SECONDS);
        assertThat(result, is(equalTo(ProcessingResult.Success)));
    }

    @Test
    public void testBatchingDispatcher() throws Exception {
        dispatcher = TaskDispatchers.createBatchingTaskDispatcher(
                "TEST",
                MAX_BUFFER_SIZE,
                WORK_LOAD_SIZE,
                1,
                MAX_BATCHING_DELAY_MS,
                SERVER_UNAVAILABLE_SLEEP_TIME_MS,
                RETRY_SLEEP_TIME_MS,
                processor
        );

        dispatcher.process(1, ProcessingResult.Success, System.currentTimeMillis() + 60 * 1000);
        dispatcher.process(2, ProcessingResult.Success, System.currentTimeMillis() + 60 * 1000);

        processor.expectSuccesses(2);
    }

    @Test
    public void testTasksAreDistributedAcrossAllWorkerThreads() throws Exception {
        int threadCount = 3;
        CountingTaskProcessor countingProcessor = new CountingTaskProcessor();

        TaskDispatcher<Integer, Boolean> dispatcher = TaskDispatchers.createBatchingTaskDispatcher(
                "TEST",
                MAX_BUFFER_SIZE,
                WORK_LOAD_SIZE,
                threadCount,
                MAX_BATCHING_DELAY_MS,
                SERVER_UNAVAILABLE_SLEEP_TIME_MS,
                RETRY_SLEEP_TIME_MS,
                countingProcessor
        );

        try {
            int loops = 1000;
            while (true) {
                countingProcessor.resetTo(loops);
                for (int i = 0; i < loops; i++) {
                    dispatcher.process(i, true, System.currentTimeMillis() + 60 * 1000);
                }
                countingProcessor.awaitCompletion();
                int minHitPerThread = (int) (loops / threadCount * 0.9);
                if (countingProcessor.lowestHit() < minHitPerThread) {
                    loops *= 2;
                } else {
                    break;
                }
                if (loops > MAX_BUFFER_SIZE) {
                    fail("Uneven load distribution");
                }
            }
        } finally {
            dispatcher.shutdown();
        }
    }

    static class CountingTaskProcessor implements TaskProcessor<Boolean> {

        final ConcurrentMap<Thread, Integer> threadHits = new ConcurrentHashMap<>();

        volatile Semaphore completionGuard;

        @Override
        public ProcessingResult process(Boolean task) {
            throw new IllegalStateException("unexpected");
        }

        @Override
        public ProcessingResult process(List<Boolean> tasks) {
            Thread currentThread = Thread.currentThread();
            Integer current = threadHits.get(currentThread);
            if (current == null) {
                threadHits.put(currentThread, tasks.size());
            } else {
                threadHits.put(currentThread, tasks.size() + current);
            }

            completionGuard.release(tasks.size());
            return ProcessingResult.Success;
        }

        void resetTo(int expectedTasks) {
            completionGuard = new Semaphore(-expectedTasks + 1);
        }

        void awaitCompletion() throws InterruptedException {
            assertThat(completionGuard.tryAcquire(5, TimeUnit.SECONDS), is(true));
        }

        int lowestHit() {
            return Collections.min(threadHits.values());
        }
    }
}