package com.netflix.discovery.converters.wrappers;

import junit.framework.Assert;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author David Liu
 */
public class CodecWrappersTest {

    private static String testWrapperName = "FOO_WRAPPER";

    @Test
    public void testRegisterNewWrapper() {
        Assert.assertNull(CodecWrappers.getEncoder(testWrapperName));
        Assert.assertNull(CodecWrappers.getDecoder(testWrapperName));

        CodecWrappers.registerWrapper(new TestWrapper());

        Assert.assertNotNull(CodecWrappers.getEncoder(testWrapperName));
        Assert.assertNotNull(CodecWrappers.getDecoder(testWrapperName));
    }

    private final class TestWrapper implements CodecWrapper {

        @Override
        public <T> T decode(String textValue, Class<T> type) throws IOException {
            return null;
        }

        @Override
        public <T> T decode(InputStream inputStream, Class<T> type) throws IOException {
            return null;
        }

        @Override
        public <T> String encode(T object) throws IOException {
            return null;
        }

        @Override
        public <T> void encode(T object, OutputStream outputStream) throws IOException {

        }

        @Override
        public String codecName() {
            return testWrapperName;
        }

        @Override
        public boolean support(MediaType mediaType) {
            return false;
        }
    }
}
