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

package com.netflix.eureka2.testkit.cli.command;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.cli.Command;
import com.netflix.eureka2.testkit.cli.Context;
import com.netflix.eureka2.testkit.cli.Session;
import com.netflix.eureka2.testkit.cli.Session.Status;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;

/**
 * @author Tomasz Bak
 */
public class RegisterCommand extends Command {

    public RegisterCommand() {
        super("register", 0);
    }

    @Override
    public String getDescription() {
        return "register with server";
    }

    @Override
    protected boolean executeCommand(Context context, String[] args) {
        Session activeSession = context.getActiveSession();

        if (!activeSession.isConnected()) {
            System.err.printf("ERROR: no active session; run connect command first");
            return false;
        }
        if (!activeSession.expectedRegistrationStatus(Status.NotStarted, Status.Failed)) {
            System.err.printf("ERROR: registration already done");
            return false;
        }

        InstanceInfo instanceInfo = SampleInstanceInfo.CliServer.build();
        activeSession.register(instanceInfo);

        return true;
    }
}
