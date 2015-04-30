package com.netflix.eureka2.codec.json;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import com.netflix.eureka2.codec.SampleObject;
import org.junit.Test;

import static com.netflix.eureka2.codec.SampleObject.CONTENT;
import static com.netflix.eureka2.codec.SampleObject.SAMPLE_OBJECT_MODEL_SET;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class EurekaJsonCodecTest {

    @Test
    public void testCodec() throws Exception {
        EurekaJsonCodec<SampleObject> codec = new EurekaJsonCodec<>(SAMPLE_OBJECT_MODEL_SET);

        assertThat(codec.accept(CONTENT.getClass()), is(true));

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        codec.encode(CONTENT, os);

        SampleObject decoded = codec.decode(new ByteArrayInputStream(os.toByteArray()));

        assertThat(decoded, is(equalTo(CONTENT)));
    }
}