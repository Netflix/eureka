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

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka.util.batcher.TaskProcessor.ProcessingResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class AcceptorExecutorTest {

    private static final long SERVER_UNAVAILABLE_SLEEP_TIME_MS = 1000;
    private static final long RETRY_SLEEP_TIME_MS = 100;
    private static final long MAX_BATCHING_DELAY_MS = 10;

    private static final int MAX_BUFFER_SIZE = 3;
    private static final int WORK_LOAD_SIZE = 2;

    private AcceptorExecutor<Integer, String> acceptorExecutor;

    @Before
    public void setUp() throws Exception {
        acceptorExecutor = new AcceptorExecutor<>(
                "TEST", MAX_BUFFER_SIZE, WORK_LOAD_SIZE, MAX_BATCHING_DELAY_MS,
                SERVER_UNAVAILABLE_SLEEP_TIME_MS, RETRY_SLEEP_TIME_MS
        );
    }

    @After
    public void tearDown() throws Exception {
        acceptorExecutor.shutdown();
    }

    @Test
    public void testTasksAreDispatchedToWorkers() throws Exception {
        acceptorExecutor.process(1, "Task1", System.currentTimeMillis() + 60 * 1000);

        TaskHolder<Integer, String> taskHolder = acceptorExecutor.requestWorkItem().poll(5, TimeUnit.SECONDS);
        verifyTaskHolder(taskHolder, 1, "Task1");

        acceptorExecutor.process(2, "Task2", System.currentTimeMillis() + 60 * 1000);

        List<TaskHolder<Integer, String>> taskHolders = acceptorExecutor.requestWorkItems().poll(5, TimeUnit.SECONDS);
        assertThat(taskHolders.size(), is(equalTo(1)));
        verifyTaskHolder(taskHolders.get(0), 2, "Task2");
    }

    @Test
    public void testBatchSizeIsConstrainedByConfiguredMaxSize() throws Exception {
        for (int i = 0; i <= MAX_BUFFER_SIZE; i++) {
            acceptorExecutor.process(i, "Task" + i, System.currentTimeMillis() + 60 * 1000);
        }

        List<TaskHolder<Integer, String>> taskHolders = acceptorExecutor.requestWorkItems().poll(5, TimeUnit.SECONDS);
        assertThat(taskHolders.size(), is(equalTo(WORK_LOAD_SIZE)));
    }

    @Test
    public void testNewTaskOverridesOldOne() throws Exception {
        acceptorExecutor.process(1, "Task1", System.currentTimeMillis() + 60 * 1000);
        acceptorExecutor.process(1, "Task1.1", System.currentTimeMillis() + 60 * 1000);

        TaskHolder<Integer, String> taskHolder = acceptorExecutor.requestWorkItem().poll(5, TimeUnit.SECONDS);
        verifyTaskHolder(taskHolder, 1, "Task1.1");
    }

    @Test
    public void testRepublishedTaskIsHandledFirst() throws Exception {
        acceptorExecutor.process(1, "Task1", System.currentTimeMillis() + 60 * 1000);
        acceptorExecutor.process(2, "Task2", System.currentTimeMillis() + 60 * 1000);

        TaskHolder<Integer, String> firstTaskHolder = acceptorExecutor.requestWorkItem().poll(5, TimeUnit.SECONDS);
        verifyTaskHolder(firstTaskHolder, 1, "Task1");

        acceptorExecutor.reprocess(firstTaskHolder, ProcessingResult.TransientError);

        TaskHolder<Integer, String> secondTaskHolder = acceptorExecutor.requestWorkItem().poll(5, TimeUnit.SECONDS);
        verifyTaskHolder(secondTaskHolder, 1, "Task1");
        TaskHolder<Integer, String> thirdTaskHolder = acceptorExecutor.requestWorkItem().poll(5, TimeUnit.SECONDS);
        verifyTaskHolder(thirdTaskHolder, 2, "Task2");
    }

    @Test
    public void testWhenBufferOverflowsOldestTasksAreRemoved() throws Exception {
        for (int i = 0; i <= MAX_BUFFER_SIZE; i++) {
            acceptorExecutor.process(i, "Task" + i, System.currentTimeMillis() + 60 * 1000);
        }

        // Task 0 should be dropped out
        TaskHolder<Integer, String> firstTaskHolder = acceptorExecutor.requestWorkItem().poll(5, TimeUnit.SECONDS);
        verifyTaskHolder(firstTaskHolder, 1, "Task1");
    }

    @Test
    public void testTasksAreDelayToMaximizeBatchSize() throws Exception {
        BlockingQueue<List<TaskHolder<Integer, String>>> taskQueue = acceptorExecutor.requestWorkItems();

        acceptorExecutor.process(1, "Task1", System.currentTimeMillis() + 60 * 1000);
        Thread.sleep(MAX_BATCHING_DELAY_MS / 2);
        acceptorExecutor.process(2, "Task2", System.currentTimeMillis() + 60 * 1000);

        List<TaskHolder<Integer, String>> taskHolders = taskQueue.poll(5, TimeUnit.SECONDS);

        assertThat(taskHolders.size(), is(equalTo(2)));
    }

    private static void verifyTaskHolder(TaskHolder<Integer, String> taskHolder, int id, String task) {
        assertThat(taskHolder, is(notNullValue()));
        assertThat(taskHolder.getId(), is(equalTo(id)));
        assertThat(taskHolder.getTask(), is(equalTo(task)));
    }
}