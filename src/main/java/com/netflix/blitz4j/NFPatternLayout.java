package com.netflix.blitz4j;

import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.PatternParser;


public class NFPatternLayout extends PatternLayout
{
    public NFPatternLayout()
    {
        super();
    }

    public NFPatternLayout(String pattern)
    {
        super(pattern);
    }

    protected PatternParser createPatternParser(String pattern)
    {
        return (PatternParser) new NFPatternParser(pattern);
    }
}
