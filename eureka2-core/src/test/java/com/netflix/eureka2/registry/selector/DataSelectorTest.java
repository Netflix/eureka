package com.netflix.eureka2.registry.selector;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.netflix.eureka2.registry.selector.DataSelector.Criteria;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class DataSelectorTest {

    @Test
    public void testEvaluatesOrSubExpressionsLeftToRight() throws Exception {
        SampleDataSelector selector = new SampleDataSelector()
                .value("A")
                .or().value("B");

        // Expect A first, and B second
        List<String> items = Arrays.asList("A", "B", "C");
        Iterator<String> queryResult = selector.applyQuery(items.iterator());
        assertThat(queryResult.next(), is(equalTo("A")));
        assertThat(queryResult.next(), is(equalTo("B")));
        assertThat(queryResult.hasNext(), is(false));

        // With reverted source list, again expect A first, and B second
        items = Arrays.asList("B", "C", "A");
        queryResult = selector.applyQuery(items.iterator());
        assertThat(queryResult.next(), is(equalTo("A")));
        assertThat(queryResult.next(), is(equalTo("B")));
        assertThat(queryResult.hasNext(), is(false));
    }

    static class SampleDataSelector extends DataSelector<String, SampleDataSelector> {
        public SampleDataSelector value(String selectionValue) {
            current.add(new ValueCriteria(selectionValue));
            return this;
        }
    }

    static class ValueCriteria extends Criteria<String, String> {
        ValueCriteria(String selectionValue) {
            super(selectionValue);
        }

        @Override
        protected boolean matches(String expected, String actual) {
            return expected.equals(actual);
        }
    }
}