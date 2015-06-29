package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.testkit.data.builder.SampleOverrides;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * FIXME: codec needs fixing
 *
 * @author David Liu
 */
@Ignore
public class OverridesDTOTest {

    @Test
    public void testSerDeser() throws Exception {
        Overrides original = SampleOverrides.generateOverrides("a", 1);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        OverridesCodec.getCodec().encode(OverridesDTO.fromOverrides(original), outputStream);

        InputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

        Overrides decoded = OverridesCodec.getCodec().decode(inputStream).toOverrides();

        Assert.assertEquals(original, decoded);
    }
}
