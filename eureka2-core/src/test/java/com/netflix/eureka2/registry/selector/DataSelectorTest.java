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

    private static final SampleData VALUE_A1 = new SampleData("A", 1);
    private static final SampleData VALUE_A2 = new SampleData("A", 2);
    private static final SampleData VALUE_B1 = new SampleData("B", 1);
    private static final SampleData VALUE_C1 = new SampleData("C", 1);

    @Test
    public void testEvaluatesOrSubExpressionsLeftToRight() throws Exception {
        SampleDataSelector selector = new SampleDataSelector()
                .text("A").number(1)
                .or().text("B");

        // Expect A1 first, and B1 second
        List<SampleData> items = Arrays.asList(VALUE_A1, VALUE_A2, VALUE_B1, VALUE_C1);
        Iterator<SampleData> queryResult = selector.applyQuery(items.iterator());
        assertThat(queryResult.next(), is(equalTo(VALUE_A1)));
        assertThat(queryResult.next(), is(equalTo(VALUE_B1)));
        assertThat(queryResult.hasNext(), is(false));

        // With reverted source list, again expect A first, and B second
        items = Arrays.asList(VALUE_C1, VALUE_A2, VALUE_B1, VALUE_A1);
        queryResult = selector.applyQuery(items.iterator());
        assertThat(queryResult.next(), is(equalTo(VALUE_A1)));
        assertThat(queryResult.next(), is(equalTo(VALUE_B1)));
        assertThat(queryResult.hasNext(), is(false));
    }

    static class SampleDataSelector extends DataSelector<SampleData, SampleDataSelector> {
        public SampleDataSelector text(String selectionValue) {
            current.add(new TextCriteria(selectionValue));
            return this;
        }

        public SampleDataSelector number(int selectionValue) {
            current.add(new NumberCriteria(selectionValue));
            return this;
        }
    }

    static class SampleData {
        private final String text;
        private final int number;

        SampleData(String text, int number) {
            this.text = text;
            this.number = number;
        }

        public String getText() {
            return text;
        }

        public int getNumber() {
            return number;
        }

        @Override
        public String toString() {
            return "SampleData{text='" + text + '\'' + ", number=" + number + '}';
        }
    }

    static class TextCriteria extends Criteria<SampleData, String> {
        TextCriteria(String selectionValue) {
            super(selectionValue);
        }

        @Override
        protected boolean matches(String expected, SampleData actual) {
            return expected.equals(actual.getText());
        }
    }

    static class NumberCriteria extends Criteria<SampleData, Integer> {
        NumberCriteria(int selectionValue) {
            super(selectionValue);
        }

        @Override
        protected boolean matches(Integer expected, SampleData actual) {
            return expected == actual.getNumber();
        }
    }
}