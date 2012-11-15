package com.netflix.blitz4j;

import java.util.Arrays;
import java.util.List;

import org.apache.log4j.helpers.FormattingInfo;
import org.apache.log4j.helpers.PatternConverter;
import org.apache.log4j.helpers.PatternParser;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;


/**
 * The Netflix custom pattern formatting class.
 *
 * Gets the calling context information from the throwable if it is available or
 * gets it from the aspects if the aspects are available.
 *
 *
 * @author kranganathan
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
		// TODO : Complete for other formatting strings
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