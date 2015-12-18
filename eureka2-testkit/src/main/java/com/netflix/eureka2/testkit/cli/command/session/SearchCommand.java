package com.netflix.eureka2.testkit.cli.command.session;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.testkit.cli.Command;
import com.netflix.eureka2.testkit.cli.Context;
import rx.Notification;
import rx.Notification.Kind;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class SearchCommand extends Command {
    public SearchCommand() {
        super("search", 1);
    }

    @Override
    public String getDescription() {
        return "search for all instances, where any field value matches the given reg-exp string";
    }

    @Override
    public String getInvocationSyntax() {
        return getName() + " <pattern>";
    }

    @Override
    protected boolean executeCommand(Context context, String[] args) {
        final String pattern = args[0];
        final Matcher matcher = new Matcher(Pattern.compile(pattern));
        Notification<ChangeNotification<InstanceInfo>> notification = context.getActiveSession()
                .getInterestClient()
                .forInterest(Interests.forFullRegistry())
                .takeWhile(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
                    @Override
                    public Boolean call(ChangeNotification<InstanceInfo> notification) {
                        return notification.getKind() != ChangeNotification.Kind.BufferSentinel;
                    }
                })
                .filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
                    @Override
                    public Boolean call(ChangeNotification<InstanceInfo> notification) {
                        return notification.getKind() == ChangeNotification.Kind.Add;
                    }
                })
                .doOnNext(new Action1<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void call(ChangeNotification<InstanceInfo> notification) {
                        InstanceInfo instanceInfo = notification.getData();
                        if (matcher.matches(instanceInfo)) {
                            System.out.println(pattern + " matches: ");
                            System.out.println(instanceInfo);
                        }
                    }
                })
                .ignoreElements()
                .timeout(30, TimeUnit.SECONDS)
                .materialize().toBlocking().firstOrDefault(null);
        if (notification.getKind() == Kind.OnError) {
            System.out.println("Search failed with an error " + notification.getThrowable());
            notification.getThrowable().printStackTrace();
            return false;
        }
        return true;
    }

    static class Matcher {
        private final Pattern pattern;

        Matcher(Pattern pattern) {
            this.pattern = pattern;
        }

        boolean matches(InstanceInfo ii) {
            return matches(
                    ii.getId(),
                    ii.getApp(),
                    ii.getAppGroup(),
                    ii.getAsg(),
                    ii.getHomePageUrl(),
                    ii.getSecureVipAddress(),
                    ii.getStatus(),
                    ii.getStatusPageUrl(),
                    ii.getVipAddress(),
                    // Sub-optimal, as complex objects are converted to string first
                    ii.getDataCenterInfo(),
                    ii.getHealthCheckUrls(),
                    ii.getMetaData(),
                    ii.getPorts()
            );
        }

        boolean matches(Object... values) {
            for (Object value : values) {
                if (value != null && pattern.matcher(value.toString()).find()) {
                    return true;
                }
            }
            return false;
        }
    }
}
