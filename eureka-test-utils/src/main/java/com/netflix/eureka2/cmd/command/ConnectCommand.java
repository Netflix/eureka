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
public abstract class ConnectCommand extends Command {

    protected ConnectCommand(String name, int paramCount) {
        super(name, paramCount);
    }

    public static Command toRegister() {
        return new ConnectToRegister();
    }

    public static Command toSubscribe() {
        return new ConnectToSubscribe();
    }

    public static Command toCluster() {
        return new ConnectCluster();
    }

    static class ConnectToRegister extends ConnectCommand {

        protected ConnectToRegister() {
            super("connect-reg", 2);
        }

        @Override
        public String getInvocationSyntax() {
            return getName() + " <host> <port>";
        }

        @Override
        public String getDescription() {
            return "connect to a write server";
        }

        @Override
        protected boolean executeCommand(Context context, String[] args) {
            Session session = context.createSession();
            session.connectToRegister(args[0], Integer.parseInt(args[1]));
            return true;
        }
    }

    static class ConnectToSubscribe extends ConnectCommand {

        protected ConnectToSubscribe() {
            super("connect-read", 2);
        }

        @Override
        public String getInvocationSyntax() {
            return getName() + " <host> <port>";
        }

        @Override
        public String getDescription() {
            return "connect to a read server";
        }

        @Override
        protected boolean executeCommand(Context context, String[] args) {
            Session session = context.createSession();
            session.connectToRead(args[0], Integer.parseInt(args[1]));
            return true;
        }
    }

    static class ConnectCluster extends ConnectCommand {

        protected ConnectCluster() {
            super("connect-cluster", 4);
        }

        @Override
        public String getInvocationSyntax() {
            return getName() + " <host> <reg_port> <disc_port> <read_vip>";
        }

        @Override
        public String getDescription() {
            return "connect to a cluster server";
        }

        @Override
        protected boolean executeCommand(Context context, String[] args) {
            Session session = context.createSession();
            session.connectToCluster(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), args[3]);
            return true;
        }
    }
}
