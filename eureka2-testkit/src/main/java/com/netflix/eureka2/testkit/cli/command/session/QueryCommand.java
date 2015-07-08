package com.netflix.eureka2.testkit.cli.command.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.netflix.eureka2.testkit.cli.Command;
import com.netflix.eureka2.testkit.cli.Context;
import rx.Subscriber;
import rx.Subscription;

/**
 * @author Tomasz Bak
 */
public class QueryCommand extends Command {

    public QueryCommand() {
        super("select", 0, Integer.MAX_VALUE);
    }

    @Override
    public String getDescription() {
        return "query registry data using SQL-like syntax";
    }

    @Override
    public String getInvocationSyntax() {
        return getName() + " <query_expression>";
    }

    @Override
    protected boolean executeCommand(Context context, String[] args) {
        String[] queryExpr = new String[args.length + 1];
        queryExpr[0] = "select";
        System.arraycopy(args, 0, queryExpr, 1, args.length);
        QueryCompiler queryCompiler = new QueryCompiler(queryExpr);
        if (queryCompiler.hasError()) {
            System.out.printf("ERROR: query syntax error (%s)\n", queryCompiler.getError());
            return false;
        }
        final String id = context.getActiveSession().nextSubscriptionId();
        List<String> fieldNames = new ArrayList<>(queryCompiler.getSelectedFieldNames());
        fieldNames.add(0, "queryId");
        final TableFormatter tableFormatter = new TableFormatter(fieldNames);
        Subscription subscription = context.getActiveSession()
                .getInterestClient()
                .forInterest(queryCompiler.getInterest())
                .filter(queryCompiler.queryFilter())
                .compose(queryCompiler.selectedView())
                .subscribe(new Subscriber<Map<String, Object>>() {
                               @Override
                               public void onCompleted() {
                                   System.out.println("Query_" + id + " COMPLETE");
                               }

                               @Override
                               public void onError(Throwable e) {
                                   System.out.println("Query_" + id + " ERROR: " + e);
                               }

                               @Override
                               public void onNext(Map<String, Object> instanceData) {
                                   instanceData.put("queryId", id);
                                   tableFormatter.format(System.out, instanceData);
                               }
                           }
                );
        context.getActiveSession().addSubscription(id, subscription);
        return true;
    }
}
