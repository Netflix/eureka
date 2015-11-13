package com.netflix.eureka2.testkit.cli.command.session;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interest.Operator;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.testkit.cli.command.session.QueryCompiler.TokenStream.TokenType;
import rx.Observable;
import rx.Observable.Transformer;
import rx.functions.Func1;

/**
 * To support better data filtering, an SQL-like syntax is provided, so a user can write query expressions like:
 * <p>
 * select (* | field1 [field2 [...]]) where [fieldA (=|like) valueA] [(and|or) ...]
 * <p>
 * If possible query conditions are translated to a composite interest, which result is further filtered to return
 * only user requested items.
 * <h1>Future extensions</h1>
 * <ul>
 *     <li>'from' clause to make a query over a set of eureka nodes</li>
 *     <li>snapshot query with sort, groupBy, etc operators</li>
 *     <li>limit operator</li>
 * </ul>
 *
 * @author Tomasz Bak
 */
public class QueryCompiler {

    public static final String FIELD_ACTION = "action";
    public static final String FIELD_ID = "id";
    public static final String FIELD_APP = "app";
    public static final String FIELD_VIP_ADDRESS = "vipAddress";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_DEFAULT_IP = "defaultIP";

    private final Exception error;
    private List<String> selectedFieldNames;
    private Func1<ChangeNotification<InstanceInfo>, Boolean> queryFilterOperator;
    private Interest<InstanceInfo> interest = Interests.forFullRegistry();

    public QueryCompiler(String[] queryText) {
        QueryStatement queryStatement = new QueryStatement(new TokenStream(queryText));
        this.error = queryStatement.getError();
        if (error != null) {
            return;
        }
        selectedFieldNames = queryStatement.getSelectStatement().getFieldNames();
        if (selectedFieldNames.isEmpty()) {
            selectedFieldNames = Arrays.asList(FIELD_ID, FIELD_APP, FIELD_VIP_ADDRESS, FIELD_STATUS, FIELD_DEFAULT_IP);
        }
        if (queryStatement.getWhereStatement() == null) {
            this.queryFilterOperator = new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
                @Override
                public Boolean call(ChangeNotification<InstanceInfo> notification) {
                    return notification.isDataNotification();
                }
            };
        } else {
            final AndExpression expression = (AndExpression) queryStatement.getWhereStatement().getExpression();
            this.queryFilterOperator = new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
                @Override
                public Boolean call(ChangeNotification<InstanceInfo> notification) {
                    if (notification.isDataNotification()) {
                        return expression.matches(notification.getData());
                    }
                    return false;
                }
            };
            for (Expression e : expression.getSubExpressions()) {
                BinaryExpression binary = (BinaryExpression) e;
                String key = binary.getField();
                if (FIELD_VIP_ADDRESS.equalsIgnoreCase(key)) {
                    this.interest = Interests.forVips(binary.isExact() ? Operator.Equals : Operator.Like, binary.getExpectedValue());
                    break;
                }
            }
        }
    }

    public boolean hasError() {
        return error != null;
    }

    public Exception getError() {
        return error;
    }

    public List<String> getSelectedFieldNames() {
        return selectedFieldNames;
    }

    public Interest<InstanceInfo> getInterest() {
        return interest;
    }

    public Func1<ChangeNotification<InstanceInfo>, Boolean> queryFilter() {
        return queryFilterOperator;
    }

    public Transformer<ChangeNotification<InstanceInfo>, Map<String, Object>> selectedView() {
        return new Transformer<ChangeNotification<InstanceInfo>, Map<String, Object>>() {
            @Override
            public Observable<Map<String, Object>> call(Observable<ChangeNotification<InstanceInfo>> source) {
                return source.map(new Func1<ChangeNotification<InstanceInfo>, Map<String, Object>>() {
                    @Override
                    public Map<String, Object> call(ChangeNotification<InstanceInfo> notification) {
                        Map<String, Object> result = new HashMap<String, Object>();
                        result.put(FIELD_ACTION, notification.getKind());
                        InstanceInfo instanceInfo = notification.getData();
                        for (String field : selectedFieldNames) {
                            if (FIELD_ID.equalsIgnoreCase(field)) {
                                result.put(FIELD_ID, instanceInfo.getId());
                            } else if (FIELD_APP.equalsIgnoreCase(field)) {
                                result.put(FIELD_APP, instanceInfo.getApp());
                            } else if (FIELD_VIP_ADDRESS.equalsIgnoreCase(field)) {
                                result.put(FIELD_VIP_ADDRESS, instanceInfo.getVipAddress());
                            } else if (FIELD_STATUS.equalsIgnoreCase(field)) {
                                result.put(FIELD_STATUS, instanceInfo.getStatus());
                            } else if (FIELD_DEFAULT_IP.equals(field)) {
                                result.put(FIELD_DEFAULT_IP, instanceInfo.getDataCenterInfo().getDefaultAddress().getHostName());
                            } else {
                                result.put(field, "N/A");
                            }
                        }
                        return result;
                    }
                });
            }
        };
    }

    interface Expression {
        boolean matches(InstanceInfo instanceInfo);
    }

    static class BinaryExpression implements Expression {
        private final String field;
        private final String expectedValue;
        private final boolean exact;

        BinaryExpression(String field, String expectedValue, boolean exact) {
            this.field = field;
            this.expectedValue = expectedValue;
            this.exact = exact;
        }

        public String getField() {
            return field;
        }

        public String getExpectedValue() {
            return expectedValue;
        }

        public boolean isExact() {
            return exact;
        }

        @Override
        public boolean matches(InstanceInfo instanceInfo) {
            String instanceFieldValue = null;
            if (FIELD_ID.equals(field)) {
                instanceFieldValue = instanceInfo.getId();
            } else if (FIELD_APP.equals(field)) {
                instanceFieldValue = instanceInfo.getApp();
            } else if (FIELD_VIP_ADDRESS.equals(field)) {
                instanceFieldValue = instanceInfo.getVipAddress();
            } else if (FIELD_STATUS.equals(field)) {
                instanceFieldValue = instanceInfo.getStatus().toString();
            } else if (FIELD_DEFAULT_IP.equals(field)) {
                instanceFieldValue = instanceInfo.getDataCenterInfo().getDefaultAddress().getHostName();
            }
            if (instanceFieldValue == null) {
                return false;
            }
            if (exact) {
                return instanceFieldValue.equals(expectedValue);
            }
            return Pattern.matches(expectedValue, instanceFieldValue);
        }
    }

    static class AndExpression implements Expression {
        private final List<Expression> subExpressions;

        AndExpression(List<Expression> subExpressions) {
            this.subExpressions = subExpressions;
        }

        public List<Expression> getSubExpressions() {
            return subExpressions;
        }

        @Override
        public boolean matches(InstanceInfo instanceInfo) {
            for (Expression e : subExpressions) {
                if (!e.matches(instanceInfo)) {
                    return false;
                }
            }
            return true;
        }
    }

    static class TokenStream {

        enum TokenType {Select, NameOrValue, Wildcard, Comma, Where, Equal, Like, And, EndOfStream}

        private final List<String> parts;

        private int curPos;
        private TokenType currentTokenType;
        private String currentTokenValue;

        TokenStream(String[] queryText) {
            this.parts = new ArrayList<>(2 * queryText.length);
            for (String part : queryText) {
                part = part.trim();
                if (part.length() > 1 && part.endsWith(",")) {
                    parts.add(part.substring(0, part.length() - 1));
                    parts.add(",");
                } else {
                    parts.add(part);
                }
            }
            advanceTo(0);
        }

        boolean hasNextToken() {
            return curPos + 1 < parts.size();
        }

        TokenType currentToken() {
            return currentTokenType;
        }

        TokenType nextToken() {
            advanceTo(curPos + 1);
            return currentToken();
        }

        String currentTokenValue() {
            return currentTokenValue;
        }

        private void advanceTo(int newPos) {
            curPos = newPos;
            currentTokenValue = null;
            if (curPos >= parts.size()) {
                currentTokenType = TokenType.EndOfStream;
                return;
            }
            String tokenValue = parts.get(curPos);
            if ("select".equalsIgnoreCase(tokenValue)) {
                currentTokenType = TokenType.Select;
                return;
            }
            if ("*".equals(tokenValue)) {
                currentTokenType = TokenType.Wildcard;
                return;
            }
            if (",".equals(tokenValue)) {
                currentTokenType = TokenType.Comma;
                return;
            }
            if ("where".equalsIgnoreCase(tokenValue)) {
                currentTokenType = TokenType.Where;
                return;
            }
            if ("=".equals(tokenValue)) {
                currentTokenType = TokenType.Equal;
                return;
            }
            if ("like".equalsIgnoreCase(tokenValue)) {
                currentTokenType = TokenType.Like;
                return;
            }
            if ("and".equalsIgnoreCase(tokenValue)) {
                currentTokenType = TokenType.And;
                return;
            }
            currentTokenType = TokenType.NameOrValue;
            currentTokenValue = tokenValue;
        }
    }

    static class StatementOrError {
        protected Exception error;

        boolean hasError() {
            return error != null;
        }

        Exception getError() {
            return error;
        }
    }

    static class QueryStatement extends StatementOrError {

        private final SelectStatement selectStatement;
        private final WhereStatement whereStatement;

        QueryStatement(TokenStream tokenStream) {
            selectStatement = new SelectStatement(tokenStream);
            if (!selectStatement.hasError()) {
                if (tokenStream.currentToken() != TokenType.EndOfStream) {
                    whereStatement = new WhereStatement(tokenStream);
                    if (whereStatement.hasError()) {
                        error = whereStatement.getError();
                        return;
                    }
                    if (tokenStream.currentToken() != TokenType.EndOfStream) {
                        error = new IllegalArgumentException("unexpected tokens following where clause");
                    }
                } else {
                    whereStatement = null;
                }
            } else {
                whereStatement = null;
                error = selectStatement.getError();
            }
        }

        public SelectStatement getSelectStatement() {
            return selectStatement;
        }

        public WhereStatement getWhereStatement() {
            return whereStatement;
        }
    }

    static class SelectStatement extends StatementOrError {

        private final List<String> fieldNames = new ArrayList<>();

        SelectStatement(TokenStream tokenStream) {
            if (tokenStream.currentTokenType != TokenType.Select) {
                error = new IllegalArgumentException("select clause not properly formed");
                return;
            }
            if (tokenStream.nextToken() == TokenType.Wildcard) {
                tokenStream.nextToken();
                return;
            }
            while (true) {
                if (tokenStream.currentToken() != TokenType.NameOrValue) {
                    error = new IllegalArgumentException("select must be followed by '*' or comma separated list of field names");
                    return;
                }
                fieldNames.add(tokenStream.currentTokenValue());
                if (tokenStream.nextToken() != TokenType.Comma) {
                    break;
                }
                tokenStream.nextToken();
            }
        }

        List<String> getFieldNames() {
            return fieldNames;
        }
    }

    static class WhereStatement extends StatementOrError {

        private Expression expression;

        WhereStatement(TokenStream tokenStream) {
            if (tokenStream.currentTokenType != TokenType.Where) {
                error = new IllegalArgumentException("where clause not properly formed");
                return;
            }
            tokenStream.nextToken();
            AndStatement andStatement = new AndStatement(tokenStream);
            if (andStatement.hasError()) {
                error = andStatement.getError();
                return;
            }
            expression = andStatement.getExpressions();
        }

        Expression getExpression() {
            return expression;
        }
    }

    static class AndStatement extends StatementOrError {

        private final List<Expression> expressions = new ArrayList<>();

        AndStatement(TokenStream tokenStream) {
            SimpleExpressionStatement simpleStatement = new SimpleExpressionStatement(tokenStream);
            if (simpleStatement.hasError()) {
                error = simpleStatement.getError();
                return;
            }
            expressions.add(simpleStatement.getExpression());
            while (tokenStream.currentTokenType == TokenType.And) {
                tokenStream.nextToken();
                simpleStatement = new SimpleExpressionStatement(tokenStream);
                if (simpleStatement.hasError()) {
                    error = simpleStatement.getError();
                    return;
                }
                expressions.add(simpleStatement.getExpression());
            }
        }

        AndExpression getExpressions() {
            return new AndExpression(expressions);
        }
    }

    static class SimpleExpressionStatement extends StatementOrError {

        private BinaryExpression expression;

        SimpleExpressionStatement(TokenStream tokenStream) {
            if (tokenStream.currentToken() != TokenType.NameOrValue) {
                error = new IllegalArgumentException("field name expected in where clause");
                return;
            }
            String fieldName = tokenStream.currentTokenValue;
            TokenType operatorType = tokenStream.nextToken();
            if (operatorType != TokenType.Equal && operatorType != TokenType.Like) {
                error = new IllegalArgumentException("operator expected in where clause");
                return;
            }
            if (tokenStream.nextToken() != TokenType.NameOrValue) {
                error = new IllegalArgumentException("operator argument expected in where clause");
                return;
            }
            expression = new BinaryExpression(fieldName, tokenStream.currentTokenValue, operatorType == TokenType.Equal);
            tokenStream.nextToken();
        }

        BinaryExpression getExpression() {
            return expression;
        }
    }
}
