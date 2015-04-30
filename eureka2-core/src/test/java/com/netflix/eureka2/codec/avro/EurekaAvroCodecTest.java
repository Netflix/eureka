package com.netflix.eureka2.codec.avro;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import com.netflix.eureka2.codec.SampleObject;
import org.junit.Test;

import static com.netflix.eureka2.codec.SampleObject.CONTENT;
import static com.netflix.eureka2.codec.SampleObject.SAMPLE_OBJECT_MODEL_SET;
import static com.netflix.eureka2.codec.SampleObject.rootSchema;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class EurekaAvroCodecTest {

    @Test
    public void testCodec() throws Exception {
        EurekaAvroCodec<SampleObject> codec = new EurekaAvroCodec<>(SAMPLE_OBJECT_MODEL_SET, rootSchema());

        assertThat(codec.accept(CONTENT.getClass()), is(true));

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        codec.encode(CONTENT, os);

        SampleObject decoded = codec.decode(new ByteArrayInputStream(os.toByteArray()));

        assertThat(decoded, is(equalTo(CONTENT)));
    }
}