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

package com.netflix.eureka2.testkit.cli.command.session;

import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.StdDelta.Builder;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfoField;
import com.netflix.eureka2.model.instance.InstanceInfoField.Name;
import com.netflix.eureka2.testkit.cli.Command;
import com.netflix.eureka2.testkit.cli.Context;
import com.netflix.eureka2.testkit.cli.Session;
import com.netflix.eureka2.testkit.cli.Session.Status;

/**
 * @author Tomasz Bak
 */
public class UpdateCommand extends Command {

    public UpdateCommand() {
        super("update", 2);
    }

    @Override
    public String getInvocationSyntax() {
        return getName() + " <field> <value>";
    }

    @Override
    public String getDescription() {
        return "update own registry entry";
    }

    @Override
    protected boolean executeCommand(Context context, String[] args) {
        Session activeSession = context.getActiveSession();

        if (!activeSession.expectedRegistrationStatus(Status.Complete)) {
            System.err.printf("ERROR: already unregistered");
            return false;
        }

        Name name = Name.valueOf(args[0]);
        InstanceInfoField<Object> field = InstanceInfoField.forName(name);

        Object value;
        if (field.getValueType().equals(Integer.class)) {
            value = Integer.parseInt(args[1]);
        } else if (field.getValueType().equals(InstanceInfo.Status.class)) {
            value = InstanceInfo.Status.valueOf(args[1]);
        } else {
            value = args[1];
        }

        InstanceInfo lastInstanceInfo = activeSession.getInstanceInfo();
        Delta<?> delta = new Builder()
                .withId(lastInstanceInfo.getId())
                .withDelta(field, value)
                .build();
        lastInstanceInfo = lastInstanceInfo.applyDelta(delta);

        activeSession.update(lastInstanceInfo);

        return true;
    }
}
