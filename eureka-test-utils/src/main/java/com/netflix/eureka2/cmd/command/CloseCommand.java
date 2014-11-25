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

package com.netflix.eureka2.cmd.command;

import com.netflix.eureka2.cmd.Command;
import com.netflix.eureka2.cmd.Context;
import com.netflix.eureka2.cmd.Session;

/**
 * @author Tomasz Bak
 */
public class CloseCommand extends Command {

    public CloseCommand() {
        super("close");
    }

    @Override
    public String getDescription() {
        return "close all channels";
    }

    @Override
    protected boolean executeCommand(Context context, String[] args) {
        Session activeSession = context.getActiveSession();

        if (!activeSession.isConnected()) {
            System.err.printf("ERROR: no active session; run connect command first");
            return false;
        }

        activeSession.close();

        return true;
    }
}
