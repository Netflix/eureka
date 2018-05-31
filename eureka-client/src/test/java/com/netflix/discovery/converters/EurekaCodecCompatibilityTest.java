package com.netflix.discovery.converters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInfo;
import com.netflix.discovery.converters.wrappers.CodecWrapper;
import com.netflix.discovery.converters.wrappers.CodecWrappers;
import com.netflix.discovery.converters.wrappers.DecoderWrapper;
import com.netflix.discovery.converters.wrappers.EncoderWrapper;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.EurekaEntityComparators;
import com.netflix.discovery.util.InstanceInfoGenerator;
import com.netflix.eureka.cluster.protocol.ReplicationInstance;
import com.netflix.eureka.cluster.protocol.ReplicationInstanceResponse;
import com.netflix.eureka.cluster.protocol.ReplicationList;
import com.netflix.eureka.cluster.protocol.ReplicationListResponse;
import com.netflix.eureka.registry.PeerAwareInstanceRegistryImpl.Action;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class EurekaCodecCompatibilityTest {

    private static final List<CodecWrapper> availableJsonWrappers = new ArrayList<>();
    private static final List<CodecWrapper> availableXmlWrappers = new ArrayList<>();

    static {
        availableJsonWrappers.add(new CodecWrappers.XStreamJson());
        availableJsonWrappers.add(new CodecWrappers.LegacyJacksonJson());
        availableJsonWrappers.add(new CodecWrappers.JacksonJson());

        availableXmlWrappers.add(new CodecWrappers.JacksonXml());
        availableXmlWrappers.add(new CodecWrappers.XStreamXml());
    }

    private final InstanceInfoGenerator infoGenerator = InstanceInfoGenerator.newBuilder(4, 2).withMetaData(true).build();
    private final Iterator<InstanceInfo> infoIterator = infoGenerator.serviceIterator();

    interface Action2 {
        void call(EncoderWrapper encodingCodec, DecoderWrapper decodingCodec) throws IOException;
    }

    /**
     * @deprecated see to do note in {@link com.netflix.appinfo.LeaseInfo} and delete once legacy is removed
     */
    @Deprecated
    @Test
    public void testInstanceInfoEncodeDecodeLegacyJacksonToJackson() throws Exception {
        final InstanceInfo instanceInfo = infoIterator.next();
        Action2 codingAction = new Action2() {
            @Override
            public void call(EncoderWrapper encodingCodec, DecoderWrapper decodingCodec) throws IOException {
                String encodedString = encodingCodec.encode(instanceInfo);
                // convert the field from the json string to what the legacy json would encode as
                encodedString = encodedString.replaceFirst("lastRenewalTimestamp", "renewalTimestamp");
                InstanceInfo decodedValue = decodingCodec.decode(encodedString, InstanceInfo.class);
                assertThat(EurekaEntityComparators.equal(instanceInfo, decodedValue, new EurekaEntityComparators.RawIdEqualFunc()), is(true));
                assertThat(EurekaEntityComparators.equal(instanceInfo, decodedValue), is(true));
            }
        };

        verifyForPair(
                codingAction,
                InstanceInfo.class,
                new CodecWrappers.LegacyJacksonJson(),
                new CodecWrappers.JacksonJson()
        );
    }

    @Test
    public void testInstanceInfoEncodeDecodeJsonWithEmptyMetadataMap() throws Exception {
        final InstanceInfo base = infoIterator.next();
        final InstanceInfo instanceInfo = new InstanceInfo.Builder(base)
                .setMetadata(Collections.EMPTY_MAP)
                .build();

        Action2 codingAction = new Action2() {
            @Override
            public void call(EncoderWrapper encodingCodec, DecoderWrapper decodingCodec) throws IOException {
                String encodedString = encodingCodec.encode(instanceInfo);
                InstanceInfo decodedValue = decodingCodec.decode(encodedString, InstanceInfo.class);
                assertThat(EurekaEntityComparators.equal(instanceInfo, decodedValue), is(true));
            }
        };

        verifyAllPairs(codingAction, Application.class, availableJsonWrappers);
        verifyAllPairs(codingAction, Application.class, availableXmlWrappers);
    }

    /**
     * During deserialization process in compact mode not all fields might be filtered out. If JVM memory
     * is an issue, compact version of the encoder should be used on the server side.
     */
    @Test
    public void testInstanceInfoFullEncodeMiniDecodeJackson() throws Exception {
        final InstanceInfo instanceInfo = infoIterator.next();
        Action2 codingAction = new Action2() {
            @Override
            public void call(EncoderWrapper encodingCodec, DecoderWrapper decodingCodec) throws IOException {
                String encodedString = encodingCodec.encode(instanceInfo);
                InstanceInfo decodedValue = decodingCodec.decode(encodedString, InstanceInfo.class);
                assertThat(EurekaEntityComparators.equalMini(instanceInfo, decodedValue), is(true));
            }
        };

        verifyForPair(
                codingAction,
                InstanceInfo.class,
                new CodecWrappers.JacksonJson(),
                new CodecWrappers.JacksonJsonMini()
        );
    }

    @Test
    public void testInstanceInfoFullEncodeMiniDecodeJacksonWithMyOwnDataCenterInfo() throws Exception {
        final InstanceInfo base = infoIterator.next();
        final InstanceInfo instanceInfo = new InstanceInfo.Builder(base)
                .setDataCenterInfo(new MyDataCenterInfo(DataCenterInfo.Name.MyOwn))
                .build();

        Action2 codingAction = new Action2() {
            @Override
            public void call(EncoderWrapper encodingCodec, DecoderWrapper decodingCodec) throws IOException {
                String encodedString = encodingCodec.encode(instanceInfo);
                InstanceInfo decodedValue = decodingCodec.decode(encodedString, InstanceInfo.class);
                assertThat(EurekaEntityComparators.equalMini(instanceInfo, decodedValue), is(true));
            }
        };

        verifyForPair(
                codingAction,
                InstanceInfo.class,
                new CodecWrappers.JacksonJson(),
                new CodecWrappers.JacksonJsonMini()
        );
    }

    @Test
    public void testInstanceInfoMiniEncodeMiniDecodeJackson() throws Exception {
        final InstanceInfo instanceInfo = infoIterator.next();
        Action2 codingAction = new Action2() {
            @Override
            public void call(EncoderWrapper encodingCodec, DecoderWrapper decodingCodec) throws IOException {
                String encodedString = encodingCodec.encode(instanceInfo);
                InstanceInfo decodedValue = decodingCodec.decode(encodedString, InstanceInfo.class);
                assertThat(EurekaEntityComparators.equalMini(instanceInfo, decodedValue), is(true));
            }
        };

        verifyForPair(
                codingAction,
                InstanceInfo.class,
                new CodecWrappers.JacksonJsonMini(),
                new CodecWrappers.JacksonJsonMini()
        );
    }

    @Test
    public void testInstanceInfoEncodeDecode() throws Exception {
        final InstanceInfo instanceInfo = infoIterator.next();
        Action2 codingAction = new Action2() {
            @Override
            public void call(EncoderWrapper encodingCodec, DecoderWrapper decodingCodec) throws IOException {
                String encodedString = encodingCodec.encode(instanceInfo);

                InstanceInfo decodedValue = decodingCodec.decode(encodedString, InstanceInfo.class);
                assertThat(EurekaEntityComparators.equal(instanceInfo, decodedValue), is(true));
                assertThat(EurekaEntityComparators.equal(instanceInfo, decodedValue, new EurekaEntityComparators.RawIdEqualFunc()), is(true));
            }
        };

        verifyAllPairs(codingAction, InstanceInfo.class, availableJsonWrappers);
        verifyAllPairs(codingAction, InstanceInfo.class, availableXmlWrappers);
    }

    // https://github.com/Netflix/eureka/issues/1051
    // test going from camel case to lower case
    @Test
    public void testInstanceInfoEncodeDecodeCompatibilityDueToOverriddenStatusRenamingV1() throws Exception {
        final InstanceInfo instanceInfo = infoIterator.next();
        new InstanceInfo.Builder(instanceInfo).setOverriddenStatus(InstanceInfo.InstanceStatus.OUT_OF_SERVICE);

        Action2 codingAction = new Action2() {
            @Override
            public void call(EncoderWrapper encodingCodec, DecoderWrapper decodingCodec) throws IOException {
                String encodedString = encodingCodec.encode(instanceInfo);
                // sed to older naming to test
                encodedString = encodedString.replace("overriddenStatus", "overriddenstatus");

                InstanceInfo decodedValue = decodingCodec.decode(encodedString, InstanceInfo.class);
                assertThat(EurekaEntityComparators.equal(instanceInfo, decodedValue), is(true));
                assertThat(EurekaEntityComparators.equal(instanceInfo, decodedValue, new EurekaEntityComparators.RawIdEqualFunc()), is(true));
            }
        };

        verifyAllPairs(codingAction, Application.class, availableJsonWrappers);
        verifyAllPairs(codingAction, Application.class, availableXmlWrappers);
    }

    // same as the above, but go from lower case to camel case
    @Test
    public void testInstanceInfoEncodeDecodeCompatibilityDueToOverriddenStatusRenamingV2() throws Exception {
        final InstanceInfo instanceInfo = infoIterator.next();
        new InstanceInfo.Builder(instanceInfo).setOverriddenStatus(InstanceInfo.InstanceStatus.OUT_OF_SERVICE);

        Action2 codingAction = new Action2() {
            @Override
            public void call(EncoderWrapper encodingCodec, DecoderWrapper decodingCodec) throws IOException {
                String encodedString = encodingCodec.encode(instanceInfo);
                // sed to older naming to test
                encodedString = encodedString.replace("overriddenstatus", "overriddenStatus");

                InstanceInfo decodedValue = decodingCodec.decode(encodedString, InstanceInfo.class);
                assertThat(EurekaEntityComparators.equal(instanceInfo, decodedValue), is(true));
                assertThat(EurekaEntityComparators.equal(instanceInfo, decodedValue, new EurekaEntityComparators.RawIdEqualFunc()), is(true));
            }
        };

        verifyAllPairs(codingAction, Application.class, availableJsonWrappers);
        verifyAllPairs(codingAction, Application.class, availableXmlWrappers);
    }

    @Test
    public void testApplicationEncodeDecode() throws Exception {
        final Application application = new Application("testApp");
        application.addInstance(infoIterator.next());
        application.addInstance(infoIterator.next());

        Action2 codingAction = new Action2() {
            @Override
            public void call(EncoderWrapper encodingCodec, DecoderWrapper decodingCodec) throws IOException {
                String encodedString = encodingCodec.encode(application);
                Application decodedValue = decodingCodec.decode(encodedString, Application.class);
                assertThat(EurekaEntityComparators.equal(application, decodedValue), is(true));
            }
        };

        verifyAllPairs(codingAction, Application.class, availableJsonWrappers);
        verifyAllPairs(codingAction, Application.class, availableXmlWrappers);
    }

    @Test
    public void testApplicationsEncodeDecode() throws Exception {
        final Applications applications = infoGenerator.takeDelta(2);

        Action2 codingAction = new Action2() {
            @Override
            public void call(EncoderWrapper encodingCodec, DecoderWrapper decodingCodec) throws IOException {
                String encodedString = encodingCodec.encode(applications);
                Applications decodedValue = decodingCodec.decode(encodedString, Applications.class);
                assertThat(EurekaEntityComparators.equal(applications, decodedValue), is(true));
            }
        };

        verifyAllPairs(codingAction, Applications.class, availableJsonWrappers);
        verifyAllPairs(codingAction, Applications.class, availableXmlWrappers);
    }

    /**
     * For backward compatibility with LegacyJacksonJson codec single item arrays shall not be unwrapped.
     */
    @Test
    public void testApplicationsJsonEncodeDecodeWithSingleAppItem() throws Exception {
        final Applications applications = infoGenerator.takeDelta(1);

        Action2 codingAction = new Action2() {
            @Override
            public void call(EncoderWrapper encodingCodec, DecoderWrapper decodingCodec) throws IOException {
                String encodedString = encodingCodec.encode(applications);

                assertThat(encodedString.contains("\"application\":[{"), is(true));

                Applications decodedValue = decodingCodec.decode(encodedString, Applications.class);
                assertThat(EurekaEntityComparators.equal(applications, decodedValue), is(true));
            }
        };

        List<CodecWrapper> jsonCodes = Arrays.asList(
                new CodecWrappers.LegacyJacksonJson(),
                new CodecWrappers.JacksonJson()
        );

        verifyAllPairs(codingAction, Applications.class, jsonCodes);
    }

    @Test
    public void testBatchRequestEncoding() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();
        List<ReplicationInstance> replicationInstances = new ArrayList<>();
        replicationInstances.add(new ReplicationInstance(
                instance.getAppName(),
                instance.getId(),
                System.currentTimeMillis(),
                null,
                instance.getStatus().name(),
                instance,
                Action.Register
        ));
        final ReplicationList replicationList = new ReplicationList(replicationInstances);

        Action2 codingAction = new Action2() {
            @Override
            public void call(EncoderWrapper encodingCodec, DecoderWrapper decodingCodec) throws IOException {
                String encodedString = encodingCodec.encode(replicationList);

                ReplicationList decodedValue = decodingCodec.decode(encodedString, ReplicationList.class);
                assertThat(decodedValue.getReplicationList().size(), is(equalTo(1)));
            }
        };

        // In replication channel we use JSON only
        List<CodecWrapper> jsonCodes = Arrays.asList(
                new CodecWrappers.JacksonJson(),
                new CodecWrappers.LegacyJacksonJson()
        );

        verifyAllPairs(codingAction, ReplicationList.class, jsonCodes);
    }

    @Test
    public void testBatchResponseEncoding() throws Exception {
        List<ReplicationInstanceResponse> responseList = new ArrayList<>();
        responseList.add(new ReplicationInstanceResponse(200, InstanceInfoGenerator.takeOne()));
        final ReplicationListResponse replicationListResponse = new ReplicationListResponse(responseList);

        Action2 codingAction = new Action2() {
            @Override
            public void call(EncoderWrapper encodingCodec, DecoderWrapper decodingCodec) throws IOException {
                String encodedString = encodingCodec.encode(replicationListResponse);

                ReplicationListResponse decodedValue = decodingCodec.decode(encodedString, ReplicationListResponse.class);
                assertThat(decodedValue.getResponseList().size(), is(equalTo(1)));
            }
        };

        // In replication channel we use JSON only
        List<CodecWrapper> jsonCodes = Arrays.asList(
                new CodecWrappers.JacksonJson(),
                new CodecWrappers.LegacyJacksonJson()
        );

        verifyAllPairs(codingAction, ReplicationListResponse.class, jsonCodes);
    }

    public void verifyAllPairs(Action2 codingAction, Class<?> typeToEncode, List<CodecWrapper> codecHolders) throws Exception {
        for (EncoderWrapper encodingCodec : codecHolders) {
            for (DecoderWrapper decodingCodec : codecHolders) {
                String pair = "{" + encodingCodec.codecName() + ',' + decodingCodec.codecName() + '}';
                System.out.println("Encoding " + typeToEncode.getSimpleName() + " using " + pair);
                try {
                    codingAction.call(encodingCodec, decodingCodec);
                } catch (Exception ex) {
                    throw new Exception("Encoding failure for codec pair " + pair, ex);
                }
            }
        }
    }

    public void verifyForPair(Action2 codingAction, Class<?> typeToEncode, EncoderWrapper encodingCodec, DecoderWrapper decodingCodec) throws Exception {
        String pair = "{" + encodingCodec.codecName() + ',' + decodingCodec.codecName() + '}';
        System.out.println("Encoding " + typeToEncode.getSimpleName() + " using " + pair);
        try {
            codingAction.call(encodingCodec, decodingCodec);
        } catch (Exception ex) {
            throw new Exception("Encoding failure for codec pair " + pair, ex);
        }
    }

}
