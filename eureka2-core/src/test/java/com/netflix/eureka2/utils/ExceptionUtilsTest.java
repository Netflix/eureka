package com.netflix.eureka2.utils;

import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class ExceptionUtilsTest {

    @Test
    public void testStackTraceTrimming() throws Exception {
        IllegalStateException exception = ExceptionUtils.trimStackTraceof(new IllegalStateException("test"));
        assertThat(exception.getStackTrace()[0].getClassName(), is(equalTo(ExceptionUtilsTest.class.getName())));
    }
}