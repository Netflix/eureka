package com.netflix.discovery.converters;

import java.io.IOException;
import java.util.Iterator;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class EurekaCodecCompatibilityTest {

    private final InstanceInfoGenerator infoGenerator = InstanceInfoGenerator.newBuilder(4, 2).withMetaData(true).build();
    private final Iterator<InstanceInfo> infoIterator = infoGenerator.serviceIterator();

    interface Action2 {
        void call(CodecWrapper encodingCodec, CodecWrapper decodingCodec) throws IOException;
    }

    @Test
    public void testInstanceInfoEncodeDecode() throws Exception {
        final InstanceInfo instanceInfo = infoIterator.next();
        Action2 codingAction = new Action2() {
            @Override
            public void call(CodecWrapper encodingCodec, CodecWrapper decodingCodec) throws IOException {
                String encodedString = encodingCodec.encode(instanceInfo);
                InstanceInfo decodedValue = decodingCodec.decode(encodedString, InstanceInfo.class);
                assertThat(EurekaEntityComparators.equal(instanceInfo, decodedValue), is(true));
            }
        };

        verifyAllPairs(codingAction, InstanceInfo.class, CodecWrapper.availableJsonCodecs());
        verifyAllPairs(codingAction, InstanceInfo.class, CodecWrapper.availableXmlCodecs());
    }

    @Test
    public void testApplicationEncodeDecode() throws Exception {
        final Application application = new Application("testApp");
        application.addInstance(infoIterator.next());
        application.addInstance(infoIterator.next());

        Action2 codingAction = new Action2() {
            @Override
            public void call(CodecWrapper encodingCodec, CodecWrapper decodingCodec) throws IOException {
                String encodedString = encodingCodec.encode(application);
                Application decodedValue = decodingCodec.decode(encodedString, Application.class);
                assertThat(EurekaEntityComparators.equal(application, decodedValue), is(true));
            }
        };

        verifyAllPairs(codingAction, Application.class, CodecWrapper.availableJsonCodecs());
        verifyAllPairs(codingAction, Application.class, CodecWrapper.availableXmlCodecs());
    }

    @Test
    public void testApplicationsEncodeDecode() throws Exception {
        final Applications applications = infoGenerator.takeDelta(2);

        Action2 codingAction = new Action2() {
            @Override
            public void call(CodecWrapper encodingCodec, CodecWrapper decodingCodec) throws IOException {
                String encodedString = encodingCodec.encode(applications);
                Applications decodedValue = decodingCodec.decode(encodedString, Applications.class);
                assertThat(EurekaEntityComparators.equal(applications, decodedValue), is(true));
            }
        };

        verifyAllPairs(codingAction, Applications.class, CodecWrapper.availableJsonCodecs());
        verifyAllPairs(codingAction, Applications.class, CodecWrapper.availableXmlCodecs());
    }

    public void verifyAllPairs(Action2 codingAction, Class<?> typeToEncode, CodecWrapper[] codecWrappers) throws Exception {
        for (CodecWrapper encodingCodec : codecWrappers) {
            for (CodecWrapper decodingCodec : codecWrappers) {
                String pair = "{" + encodingCodec.getCodecType() + ',' + decodingCodec.getCodecType() + '}';
                System.out.println("Encoding " + typeToEncode.getSimpleName() + " using " + pair);
                try {
                    codingAction.call(encodingCodec, decodingCodec);
                } catch (Exception ex) {
                    throw new Exception("Encoding failure for codec pair " + pair, ex);
                }
            }
        }
    }
}
