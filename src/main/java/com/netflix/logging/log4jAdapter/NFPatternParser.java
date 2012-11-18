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

import java.util.Arrays;
import java.util.List;

import org.apache.log4j.helpers.FormattingInfo;
import org.apache.log4j.helpers.PatternConverter;
import org.apache.log4j.helpers.PatternParser;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;

import com.netflix.blitz4j.LoggingContext;


/**
 * A custom parser class that provides a better performing implementation than the one in log4j for finding location information such
 * as class, line number etc.
 *
 * @author Karthik Ranganathan
 *
 */
public class NFPatternParser extends PatternParser {
  	private static List<Character> contextCharList = Arrays.asList(Character.valueOf('c'),
	        Character.valueOf('l'),
	        Character.valueOf('M'),
	        Character.valueOf('C'),
	        Character.valueOf('L'),
	        Character.valueOf('F'));

	public NFPatternParser(String pattern) {
		super(pattern);
	    
	}

	protected void finalizeConverter(char c) {
		if (contextCharList.contains(Character.valueOf(c))) {
			PatternConverter pc = new NFPatternConverter(formattingInfo, c);
			addConverter(pc);
			currentLiteral.setLength(0);
		} else {
			super.finalizeConverter(c);
		}
	}

	private static class NFPatternConverter extends PatternConverter {
	    private char type;

		NFPatternConverter(FormattingInfo formattingInfo, char type) {
			super(formattingInfo);
			this.type = type;
		}

		@Override
		public String convert(LoggingEvent event) {
		    LocationInfo locationInfo = LoggingContext.getInstance().getLocationInfo(event);
		    if (locationInfo == null) {
		        return "";
		    }
		    switch (type) {
		    case 'M':
		        return locationInfo.getMethodName();
		    case 'c':
		        return event.getLoggerName();
		    case 'C':
		        return locationInfo.getClassName();
		    case 'L':
		        return locationInfo.getLineNumber();
		    case 'l':
		        return (locationInfo.getFileName() + ":"
		                + locationInfo.getClassName() + " "
		                + locationInfo.getLineNumber() + " " + locationInfo
		                .getMethodName());
		    case 'F':
		        return locationInfo.getFileName();
		    }
		    return "";
		    
		}

		
	}
}