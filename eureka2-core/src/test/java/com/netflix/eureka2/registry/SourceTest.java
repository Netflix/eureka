package com.netflix.eureka2.registry;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Mainly for testing the matchers
 *
 * @author David Liu
 */
public class SourceTest {

    private final Source originOnly1 = new Source(Source.Origin.REPLICATED);
    private final Source originOnly2 = new Source(Source.Origin.INTERESTED);

    private final Source originAndName1 = new Source(Source.Origin.REPLICATED, "someId");
    private final Source originAndName2 = new Source(Source.Origin.REPLICATED, "someOtherId");
    private final Source originAndName3 = new Source(Source.Origin.INTERESTED, "someId");

    @Test
    public void testMatcherForSource() {
        Source.SourceMatcher matcher = Source.matcherFor(originOnly1);

        assertTrue(matcher.match(originOnly1));
        assertFalse(matcher.match(originAndName1));
        assertFalse(matcher.match(originAndName2));
        assertFalse(matcher.match(originOnly2));
        assertFalse(matcher.match(originAndName3));
        assertFalse(matcher.match(null));

        Source.SourceMatcher nullMatcher = Source.matcherFor((Source)null);
        assertFalse(nullMatcher.match(originOnly1));  // just test a couple
        assertFalse(nullMatcher.match(originAndName1));
    }

    @Test
    public void testMatcherForOrigin() {
        Source.SourceMatcher matcher = Source.matcherFor(originOnly1.getOrigin());

        assertTrue(matcher.match(originOnly1));
        assertTrue(matcher.match(originAndName1));
        assertTrue(matcher.match(originAndName2));
        assertFalse(matcher.match(originOnly2));
        assertFalse(matcher.match(originAndName3));
        assertFalse(matcher.match(null));
    }

    @Test
    public void testMatcherForOriginAndName() {
        Source.SourceMatcher matcher = Source.matcherFor(originAndName1.getOrigin(), originAndName1.getName());

        assertFalse(matcher.match(originOnly1));
        assertTrue(matcher.match(originAndName1));
        assertFalse(matcher.match(originAndName2));
        assertFalse(matcher.match(originOnly2));
        assertFalse(matcher.match(originAndName3));
        assertFalse(matcher.match(null));
    }

}
