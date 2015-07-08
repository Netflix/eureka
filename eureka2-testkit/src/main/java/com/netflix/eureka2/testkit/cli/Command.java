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

/**
 * @author Tomasz Bak
 */
public abstract class Command {
    private final String name;
    private final int paramCountFrom;
    private final int paramCountTo;

    protected Command(String name) {
        this(name, -1, -1);
    }

    protected Command(String name, int paramCount) {
        this(name, paramCount, paramCount);
    }

    protected Command(String name, int paramCountFrom, int paramCountTo) {
        this.name = name;
        this.paramCountFrom = paramCountFrom;
        this.paramCountTo = paramCountTo;
    }

    public String getName() {
        return name;
    }

    public String getInvocationSyntax() {
        return name;
    }

    public abstract String getDescription();

    public boolean execute(Context context, String... args) {
        if (paramCountFrom != -1 && (args.length < paramCountFrom || args.length > paramCountTo)) {
            if (paramCountFrom != paramCountTo) {
                System.err.format("ERROR: command %s expects between %d and %d arguments, while there are %d provided\n",
                        name, paramCountFrom, paramCountTo, args.length);
            } else {
                System.err.format("ERROR: command %s expects %d arguments, while there are %d provided\n", name, paramCountFrom, args.length);
            }
            return false;
        }
        return executeCommand(context, args);
    }

    protected abstract boolean executeCommand(Context context, String[] args);
}
