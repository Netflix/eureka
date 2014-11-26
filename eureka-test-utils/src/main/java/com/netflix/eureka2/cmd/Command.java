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

package com.netflix.eureka2.cmd;

/**
 * @author Tomasz Bak
 */
public abstract class Command {
    private final String name;
    private final int paramCount;

    protected Command(String name) {
        this.name = name;
        this.paramCount = -1;
    }

    protected Command(String name, int paramCount) {
        this.name = name;
        this.paramCount = paramCount;
    }

    public String getName() {
        return name;
    }

    public String getInvocationSyntax() {
        return name;
    }

    public abstract String getDescription();

    public boolean execute(Context context, String... args) {
        if (paramCount != -1 && args.length != paramCount) {
            System.err.format("ERROR: command %s expects %d arguments, while there are %d provided\n", name, paramCount, args.length);
            return false;
        }
        return executeCommand(context, args);
    }

    protected abstract boolean executeCommand(Context context, String[] args);
}
