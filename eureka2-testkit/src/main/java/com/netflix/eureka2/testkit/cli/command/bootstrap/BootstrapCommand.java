package com.netflix.eureka2.testkit.cli.command.bootstrap;

import com.netflix.eureka2.testkit.cli.bootstrap.ClusterResolver;
import com.netflix.eureka2.testkit.cli.Command;
import com.netflix.eureka2.testkit.cli.Context;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
public class BootstrapCommand extends Command {
    public BootstrapCommand() {
        super("bootstrap", 1, 4);
    }

    @Override
    public String getDescription() {
        return "resolve Eureka deployment";
    }

    @Override
    public String getInvocationSyntax() {
        return getName() + " <host> [<interest_port>] [bootstrapVip] [readClusterVip]";
    }

    @Override
    protected boolean executeCommand(Context context, String[] args) {
        String address = args[0];
        int interestPort = -1;
        String bootstrapVip = null;
        String readClusterVip = null;
        if (args.length == 1) {
            System.out.println("Bootstrapping with address " + address + "; guessing remaining values...");
        } else {
            int aidx = 1;
            try {
                interestPort = Integer.parseInt(args[aidx]);
                aidx++;
            } catch (Exception ignored) {
            }
            if (aidx < args.length) {
                bootstrapVip = args[aidx];
                aidx++;
                if (aidx < args.length) {
                    readClusterVip = args[aidx];
                }
            }
            System.out.printf("Bootstrapping with address=%s, interestPort=%d, bootstrapVip=%s, readClusterVip=%s",
                    address, interestPort, bootstrapVip, readClusterVip);
        }
        ClusterResolver clusterResolver = new ClusterResolver(address, interestPort, bootstrapVip, readClusterVip, null, Schedulers.computation());
        context.setClusterResolver(clusterResolver);
        return true;
    }
}
