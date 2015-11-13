package com.netflix.eureka2.eureka1.rest;

import java.io.IOException;

import com.netflix.eureka2.eureka1.rest.codec.Eureka1DataCodec.EncodingFormat;
import com.netflix.eureka2.model.StdModelsInjector;
import org.junit.Test;

import static com.netflix.eureka2.eureka1.rest.AbstractEureka1RequestHandler.parseRequestFormat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class AbstractEureka1RequestHandlerTest {

    static {
        StdModelsInjector.injectStdModels();
    }

    @Test
    public void testAcceptHeaderMatchedToProperEncodingType() throws Exception {
        assertThat(parseRequestFormat("application/json"), is(equalTo(EncodingFormat.Json)));
        assertThat(parseRequestFormat("application/xml"), is(equalTo(EncodingFormat.Xml)));
        assertThat(parseRequestFormat("application/*"), is(equalTo(EncodingFormat.Json)));
        assertThat(parseRequestFormat("*/*"), is(equalTo(EncodingFormat.Json)));
        assertThat(parseRequestFormat(null), is(equalTo(EncodingFormat.Json)));
    }

    @Test(expected = IOException.class)
    public void testAcceptHeaderWithUnsupportedMediaTypeThrowsException() throws Exception {
        parseRequestFormat("plain/text");
    }
}