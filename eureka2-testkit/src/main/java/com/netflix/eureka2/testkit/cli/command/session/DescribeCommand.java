package com.netflix.eureka2.testkit.cli.command.session;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest.Operator;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.cli.Command;
import com.netflix.eureka2.testkit.cli.Context;
import com.netflix.eureka2.utils.Json;
import rx.Notification;
import rx.Notification.Kind;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class DescribeCommand extends Command {
    public DescribeCommand() {
        super("descr", 1);
    }

    @Override
    public String getDescription() {
        return "print full data record of an instance with the given id";
    }

    @Override
    public String getInvocationSyntax() {
        return getName() + " <instance_id>";
    }

    @Override
    protected boolean executeCommand(Context context, String[] args) {
        String instanceId = args[0];
        Notification<ChangeNotification<InstanceInfo>> notification = context.getActiveSession()
                .getInterestClient()
                .forInterest(Interests.forInstance(Operator.Equals, instanceId))
                .filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
                    @Override
                    public Boolean call(ChangeNotification<InstanceInfo> notification) {
                        return notification.getKind() == ChangeNotification.Kind.Add;
                    }
                })
                .take(1)
                .timeout(2, TimeUnit.SECONDS)
                .materialize().toBlocking().firstOrDefault(null);
        if (notification.getKind() == Kind.OnNext) {
            try {
                System.out.println();
                System.out.println(Json.getFormattedMapper().writeValueAsString(notification.getValue().getData()));
            } catch (IOException e) {
                System.out.printf("ERROR: cannot convert object with id %s to JSON (%s)", instanceId, e);
                return false;
            }
            return true;
        }
        System.out.printf("ERROR: instance with id %s not found", instanceId);
        return false;
    }
}
