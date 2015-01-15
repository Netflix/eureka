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

import com.netflix.eureka2.testkit.cli.Command;
import com.netflix.eureka2.testkit.cli.Context;
import com.netflix.eureka2.testkit.cli.Session;
import com.netflix.eureka2.interests.Interest.Operator;
import com.netflix.eureka2.interests.Interests;

/**
 * @author Tomasz Bak
 */
public abstract class InterestCommand extends Command {

    protected InterestCommand(String name, int paramCount) {
        super(name, paramCount);
    }

    @Override
    protected boolean executeCommand(Context context, String[] args) {
        Session activeSession = context.getActiveSession();

        if (!activeSession.isConnected()) {
            System.err.printf("ERROR: no active session; run connect command first");
            return false;
        }

        subscribeToInterest(activeSession, args);
        return true;
    }

    protected abstract void subscribeToInterest(Session activeSession, String[] args);

    public static Command forFullRegistry() {
        return new InterestCommand("interestAll", 0) {
            @Override
            public String getDescription() {
                return "start interest subscription for all";
            }

            @Override
            protected void subscribeToInterest(Session activeSession, String[] args) {
                activeSession.forInterest(Interests.forFullRegistry());
            }
        };
    }

    public static Command forVips() {
        return new InterestCommand("interest", -1) {

            @Override
            public String getInvocationSyntax() {
                return getName() + " <vipName> <vipName> ...";
            }

            @Override
            public String getDescription() {
                return "start interest subscription for given vips";
            }

            @Override
            protected void subscribeToInterest(Session activeSession, String[] args) {
                activeSession.forInterest(Interests.forVips(Operator.Like, args));
            }
        };
    }
}
