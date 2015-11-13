package com.netflix.eureka2.testkit.cli.command.session;

import com.netflix.eureka2.model.StdModelsInjector;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.interest.StdVipInterest;
import org.junit.Test;

import static com.netflix.eureka2.testkit.cli.command.session.QueryCompiler.FIELD_VIP_ADDRESS;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Tomasz Bak
 */
public class QueryCompilerTest {

    static {
        StdModelsInjector.injectStdModels();
    }

    @Test
    public void testSelectWithWildcardAndNoWhereClause() throws Exception {
        QueryCompiler queryCompiler = new QueryCompiler(new String[]{"select", "*"});
        verifyNoError(queryCompiler);
        assertThat(queryCompiler.getInterest(), is(equalTo(Interests.forFullRegistry())));
    }

    @Test
    public void testSelectWithFieldNamesAndNoWhereClause() throws Exception {
        QueryCompiler queryCompiler = new QueryCompiler(new String[]{"select", "id,", "app"});
        verifyNoError(queryCompiler);
        assertThat(queryCompiler.getInterest(), is(equalTo(Interests.forFullRegistry())));
    }

    @Test
    public void testSelectWithWildcardAndSimpleWhereClause() throws Exception {
        QueryCompiler queryCompiler = new QueryCompiler(new String[]{"select", "*", "where", FIELD_VIP_ADDRESS, "=", "123"});
        verifyNoError(queryCompiler);
        assertThat(queryCompiler.getInterest() instanceof StdVipInterest, is(true));
    }

    @Test
    public void testSelectWithWildcardAndComplexWhereClause() throws Exception {
        QueryCompiler queryCompiler = new QueryCompiler(new String[]{"select", "*", "where", FIELD_VIP_ADDRESS, "=", "123", "and", "app", "like", "eureka.*"});
        verifyNoError(queryCompiler);
        assertThat(queryCompiler.getInterest() instanceof StdVipInterest, is(true));
    }

    @Test
    public void testSelectWithFieldNamesAndComplexWhereClause() throws Exception {
        QueryCompiler queryCompiler = new QueryCompiler(new String[]{"select", "id", ",", "app", "where", FIELD_VIP_ADDRESS, "=", "123", "and", "app", "like", "eureka.*"});
        verifyNoError(queryCompiler);
        assertThat(queryCompiler.getInterest() instanceof StdVipInterest, is(true));
    }

    private static void verifyNoError(QueryCompiler queryCompiler) {
        if (queryCompiler.hasError()) {
            fail(queryCompiler.getError().getMessage());
        }
    }
}