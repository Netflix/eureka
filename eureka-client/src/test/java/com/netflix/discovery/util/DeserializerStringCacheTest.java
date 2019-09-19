package com.netflix.discovery.util;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.netflix.discovery.util.DeserializerStringCache.CacheScope;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DeserializerStringCacheTest {

    @Test
    public void testUppercaseConversionWithLowercasePreset() throws IOException {
        DeserializationContext deserializationContext = mock(DeserializationContext.class);
        DeserializerStringCache deserializerStringCache = DeserializerStringCache.from(deserializationContext);

        String lowerCaseValue = deserializerStringCache.apply("value", CacheScope.APPLICATION_SCOPE);
        assertThat(lowerCaseValue, is("value"));

        JsonParser jsonParser = mock(JsonParser.class);
        when(jsonParser.getTextCharacters()).thenReturn(new char[] {'v', 'a', 'l', 'u', 'e'});
        when(jsonParser.getTextLength()).thenReturn(5);

        String upperCaseValue = deserializerStringCache.apply(jsonParser, CacheScope.APPLICATION_SCOPE, () -> "VALUE");
        assertThat(upperCaseValue, is("VALUE"));
    }
}
