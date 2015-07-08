package com.netflix.eureka2.testkit.cli.command.session;

import com.netflix.eureka2.testkit.cli.Command;
import com.netflix.eureka2.testkit.cli.Context;

/**
 * @author Tomasz Bak
 */
public class DisconnectCommand extends Command {
    public DisconnectCommand() {
        super("disconnect", 1);
    }

    @Override
    public String getDescription() {
        return "close a session with the given id";
    }

    @Override
    public String getInvocationSyntax() {
        return getName() + " <session_id>";
    }

    @Override
    protected boolean executeCommand(Context context, String[] args) {
        String sessionId = args[0];
        if (context.getActiveSession().disconnect(sessionId)) {
            System.out.println("Session " + sessionId + " disconnected");
            return true;
        }
        System.out.println("Session " + sessionId + " does not exist");
        return false;
    }
}
