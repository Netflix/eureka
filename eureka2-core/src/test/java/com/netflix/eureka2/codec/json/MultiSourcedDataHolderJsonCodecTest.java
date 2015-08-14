package com.netflix.eureka2.codec.json;

import java.io.ByteArrayOutputStream;

import com.netflix.eureka2.registry.MultiSourcedDataHolder;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Source.Origin;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class MultiSourcedDataHolderJsonCodecTest {

    private static final InstanceInfo INSTANCE = SampleInstanceInfo.WebServer.build();

    private static final MultiSourcedDataHolder<InstanceInfo> HOLDER = buildOf(
            INSTANCE,
            new Source(Origin.LOCAL, "source1", 1),
            new Source(Origin.REPLICATED, "source2", 2)
    );

    @Test
    public void testCompleteDataEncoding() throws Exception {
        MultiSourcedDataHolderJsonCodec codec = new MultiSourcedDataHolderJsonCodec(false);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        codec.encode(HOLDER, bo);

        String jsonValue = new String(bo.toByteArray());
        assertThat(jsonValue.contains(INSTANCE.getId()), is(true));
    }

    @Test
    public void testCompactDataEncoding() throws Exception {
        MultiSourcedDataHolderJsonCodec codec = new MultiSourcedDataHolderJsonCodec(true);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        codec.encode(HOLDER, bo);

        String jsonValue = new String(bo.toByteArray());
        assertThat(jsonValue.contains(INSTANCE.getId()), is(true));
    }

    private static MultiSourcedDataHolder<InstanceInfo> buildOf(InstanceInfo instanceInfo, Source... sources) {
        MultiSourcedDataHolder<InstanceInfo> mockValue = mock(MultiSourcedDataHolder.class);
        when(mockValue.getAllSources()).thenReturn(asList(sources));
        when(mockValue.get()).thenReturn(instanceInfo);
        when(mockValue.get(any(Source.class))).thenReturn(instanceInfo);
        return mockValue;
    }
}