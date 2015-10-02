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
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka.util.batcher.TaskProcessor.ProcessingResult;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
class RecordingProcessor implements TaskProcessor<ProcessingResult> {

    final BlockingDeque<ProcessingResult> completedTasks = new LinkedBlockingDeque<>();
    final BlockingDeque<ProcessingResult> transientErrorTasks = new LinkedBlockingDeque<>();
    final BlockingDeque<ProcessingResult> permanentErrorTasks = new LinkedBlockingDeque<>();

    @Override
    public ProcessingResult process(ProcessingResult task) {
        switch (task) {
            case Success:
                completedTasks.add(task);
                break;
            case PermanentError:
                permanentErrorTasks.add(task);
                break;
            case TransientError:
                transientErrorTasks.add(task);
                break;
        }
        return task;
    }

    @Override
    public ProcessingResult process(List<ProcessingResult> tasks) {
        for (ProcessingResult task : tasks) {
            process(task);
        }
        return tasks.get(0);
    }

    public static TaskHolder<Integer, ProcessingResult> successfulTaskHolder(int id) {
        return new TaskHolder<>(id, ProcessingResult.Success, System.currentTimeMillis() + 60 * 1000);
    }

    public static TaskHolder<Integer, ProcessingResult> transientErrorTaskHolder(int id) {
        return new TaskHolder<>(id, ProcessingResult.TransientError, System.currentTimeMillis() + 60 * 1000);
    }

    public static TaskHolder<Integer, ProcessingResult> permanentErrorTaskHolder(int id) {
        return new TaskHolder<>(id, ProcessingResult.PermanentError, System.currentTimeMillis() + 60 * 1000);
    }

    public void expectSuccesses(int count) throws InterruptedException {
        for (int i = 0; i < count; i++) {
            ProcessingResult task = completedTasks.poll(5, TimeUnit.SECONDS);
            assertThat(task, is(notNullValue()));
        }
    }

    public void expectTransientErrors(int count) throws InterruptedException {
        for (int i = 0; i < count; i++) {
            ProcessingResult task = transientErrorTasks.poll(5, TimeUnit.SECONDS);
            assertThat(task, is(notNullValue()));
        }
    }

    public void expectPermanentErrors(int count) throws InterruptedException {
        for (int i = 0; i < count; i++) {
            ProcessingResult task = permanentErrorTasks.poll(5, TimeUnit.SECONDS);
            assertThat(task, is(notNullValue()));
        }
    }
}
