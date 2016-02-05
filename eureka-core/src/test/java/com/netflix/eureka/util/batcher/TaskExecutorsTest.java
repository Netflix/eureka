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
import java.util.concurrent.LinkedBlockingDeque;

import com.netflix.eureka.util.batcher.TaskProcessor.ProcessingResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.netflix.eureka.util.batcher.RecordingProcessor.permanentErrorTaskHolder;
import static com.netflix.eureka.util.batcher.RecordingProcessor.successfulTaskHolder;
import static com.netflix.eureka.util.batcher.RecordingProcessor.transientErrorTaskHolder;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class TaskExecutorsTest {

    @SuppressWarnings("unchecked")
    private final AcceptorExecutor<Integer, ProcessingResult> acceptorExecutor = mock(AcceptorExecutor.class);
    private final RecordingProcessor processor = new RecordingProcessor();

    private final BlockingQueue<TaskHolder<Integer, ProcessingResult>> taskQueue = new LinkedBlockingDeque<>();
    private final BlockingQueue<List<TaskHolder<Integer, ProcessingResult>>> taskBatchQueue = new LinkedBlockingDeque<>();

    private TaskExecutors<Integer, ProcessingResult> taskExecutors;

    @Before
    public void setUp() throws Exception {
        when(acceptorExecutor.requestWorkItem()).thenReturn(taskQueue);
        when(acceptorExecutor.requestWorkItems()).thenReturn(taskBatchQueue);
    }

    @After
    public void tearDown() throws Exception {
        taskExecutors.shutdown();
    }

    @Test
    public void testSingleItemSuccessfulProcessing() throws Exception {
        taskExecutors = TaskExecutors.singleItemExecutors("TEST", 1, processor, acceptorExecutor);
        taskQueue.add(successfulTaskHolder(1));
        processor.expectSuccesses(1);
    }

    @Test
    public void testBatchSuccessfulProcessing() throws Exception {
        taskExecutors = TaskExecutors.batchExecutors("TEST", 1, processor, acceptorExecutor);
        taskBatchQueue.add(asList(successfulTaskHolder(1), successfulTaskHolder(2)));
        processor.expectSuccesses(2);
    }

    @Test
    public void testSingleItemProcessingWithTransientError() throws Exception {
        taskExecutors = TaskExecutors.singleItemExecutors("TEST", 1, processor, acceptorExecutor);

        TaskHolder<Integer, ProcessingResult> taskHolder = transientErrorTaskHolder(1);
        taskQueue.add(taskHolder);

        // Verify that transient task is be re-scheduled
        processor.expectTransientErrors(1);
        verify(acceptorExecutor, timeout(500).times(1)).reprocess(taskHolder, ProcessingResult.TransientError);
    }

    @Test
    public void testBatchProcessingWithTransientError() throws Exception {
        taskExecutors = TaskExecutors.batchExecutors("TEST", 1, processor, acceptorExecutor);

        List<TaskHolder<Integer, ProcessingResult>> taskHolderBatch = asList(transientErrorTaskHolder(1), transientErrorTaskHolder(2));
        taskBatchQueue.add(taskHolderBatch);

        // Verify that transient task is be re-scheduled
        processor.expectTransientErrors(2);
        verify(acceptorExecutor, timeout(500).times(1)).reprocess(taskHolderBatch, ProcessingResult.TransientError);
    }

    @Test
    public void testSingleItemProcessingWithPermanentError() throws Exception {
        taskExecutors = TaskExecutors.singleItemExecutors("TEST", 1, processor, acceptorExecutor);

        TaskHolder<Integer, ProcessingResult> taskHolder = permanentErrorTaskHolder(1);
        taskQueue.add(taskHolder);

        // Verify that transient task is re-scheduled
        processor.expectPermanentErrors(1);
        verify(acceptorExecutor, never()).reprocess(taskHolder, ProcessingResult.TransientError);
    }

    @Test
    public void testBatchProcessingWithPermanentError() throws Exception {
        taskExecutors = TaskExecutors.batchExecutors("TEST", 1, processor, acceptorExecutor);

        List<TaskHolder<Integer, ProcessingResult>> taskHolderBatch = asList(permanentErrorTaskHolder(1), permanentErrorTaskHolder(2));
        taskBatchQueue.add(taskHolderBatch);

        // Verify that transient task is re-scheduled
        processor.expectPermanentErrors(2);
        verify(acceptorExecutor, never()).reprocess(taskHolderBatch, ProcessingResult.TransientError);
    }
}