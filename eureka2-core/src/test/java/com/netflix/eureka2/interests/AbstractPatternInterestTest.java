package com.netflix.eureka2.interests;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author David Liu
 */
public class AbstractPatternInterestTest {

    @Test
    public void testCompiledPatternNotPartOfEqualsOrHashCode() {
        String toMatch = "hello";

        TestPatternInterest interest = new TestPatternInterest(toMatch, Interest.Operator.Like);
        Assert.assertNull(interest.getCompiledPattern());

        TestPatternInterest interestCompiled = new TestPatternInterest(toMatch, Interest.Operator.Like);
        interestCompiled.matches(toMatch);  // compiles the pattern
        Assert.assertNotNull(interestCompiled.getCompiledPattern());

        Assert.assertEquals(interest, interestCompiled);
    }

    static class TestPatternInterest extends AbstractPatternInterest<String> {

        public TestPatternInterest(String pattern, Operator operator) {
            super(pattern, operator);
        }

        @Override
        protected String getValue(String data) {
            return data;
        }

        @Override
        public boolean isAtomicInterest() {
            return true;
        }
    }
}
