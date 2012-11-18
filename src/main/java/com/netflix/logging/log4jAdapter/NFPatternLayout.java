/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.logging.log4jAdapter;

import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.PatternParser;

/**
 * Custom Pattern Layout class for less contended implementation.
 * 
 * @author Karthik Ranganathan
 *
 */
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
