package com.netflix.eureka2.utils;

import java.util.Map;

import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class ConfigProxyUtilsTest {

    interface SampleBase {
        String getString();
    }

    interface SampleExt extends SampleBase {
        boolean isBoolean();

        boolean hasValue();
    }

    private final SampleExt sample = new SampleExt() {
        @Override
        public boolean isBoolean() {
            return true;
        }

        @Override
        public boolean hasValue() {
            return true;
        }

        @Override
        public String getString() {
            return "stringValue";
        }
    };

    @Test
    public void testAsMap() throws Exception {
        Map<String, Object> map = ConfigProxyUtils.asMap(sample);
        assertThat((String) map.get("string"), is(equalTo("stringValue")));
        assertThat((Boolean) map.get("boolean"), is(true));
        assertThat((Boolean) map.get("value"), is(true));
    }
}