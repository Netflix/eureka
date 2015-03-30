package com.netflix.eureka2.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.functions.InterestFunctions;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.protocol.registration.Register;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.transport.codec.avro.AvroCodec;
import com.netflix.eureka2.transport.codec.avro.SchemaReflectData;
import com.netflix.eureka2.transport.codec.json.JsonCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.ByteToMessageCodec;

import static com.netflix.eureka2.transport.EurekaTransports.REGISTRATION_AVRO_SCHEMA;
import static com.netflix.eureka2.transport.EurekaTransports.REGISTRATION_PROTOCOL_MODEL_SET;

/**
 * Read registry content and examine data encoding efficiency with respect to:
 * <ul>
 *     <li>- individual instance info objects</li>
 *     <li>- compressed individual instance info objects</li>
 *     <li>- compressed batch of instance info objects</li>
 * </ul>
 *
 * @author Tomasz Bak
 */
public class InstanceInfoEncodingEfficiency {

    private final SchemaReflectData avroReflectData = new SchemaReflectData(REGISTRATION_AVRO_SCHEMA);

    private final EurekaInterestClient interestClient;
    private List<InstanceInfo> instanceInfos;

    private final AvroCodec avroCodec = new AvroCodec(REGISTRATION_PROTOCOL_MODEL_SET, REGISTRATION_AVRO_SCHEMA, avroReflectData);
    private final JsonCodec jsonCodec = new JsonCodec(REGISTRATION_PROTOCOL_MODEL_SET);

    public InstanceInfoEncodingEfficiency(String writeServerDns) {
        interestClient = Eurekas.newInterestClientBuilder().withServerResolver(
                ServerResolvers.fromDnsName(writeServerDns).withPort(EurekaTransports.DEFAULT_DISCOVERY_PORT)
        ).build();
    }

    public void loadData() {
        instanceInfos = new ArrayList<>(
                interestClient.forInterest(Interests.forFullRegistry())
                        .compose(InterestFunctions.buffers())
                        .compose(InterestFunctions.snapshots())
                        .toBlocking().first()
        );
        System.out.printf("Loaded %d instances", instanceInfos.size());
    }

    public void instanceInfoEncoding() throws IOException {
        System.out.println("Avro");
        System.out.println("----------------------------");
        instanceInfoEncoding(avroCodec);

        System.out.println("\nJSON");
        System.out.println("----------------------------");
        instanceInfoEncoding(jsonCodec);
    }

    private void instanceInfoEncoding(ByteToMessageCodec codec) throws IOException {
        EmbeddedChannel ch = new EmbeddedChannel(codec);

        long total = 0;
        long totalGzip = 0;

        ByteArrayOutputStream totalBOS = new ByteArrayOutputStream();
        GZIPOutputStream totalGOS = new GZIPOutputStream(totalBOS);

        for (InstanceInfo data : instanceInfos) {
            byte[] encoded = encode(ch, new Register(data));

            total += encoded.length;
            totalGzip += gzipSize(encoded);

            totalBOS.write(encoded);
        }
        totalGOS.close();

        System.out.println("Total uncompressed: " + total);
        System.out.println("Total compressed: " + gzipSize(totalBOS.toByteArray()));
        System.out.println("Average uncompressed: " + total / instanceInfos.size());
        System.out.println("Average compressed: " + totalGzip / instanceInfos.size());
    }

    private static <T> byte[] encode(EmbeddedChannel ch, Register object) {
        ch.writeOutbound(object);
        ByteBuf byteBuf = (ByteBuf) ch.readOutbound();
        int len = byteBuf.readableBytes();
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            result[i] = byteBuf.readByte();
        }
        return result;
    }

    private static int gzipSize(byte[] avroEncoded) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        GZIPOutputStream gos = new GZIPOutputStream(os);
        gos.write(avroEncoded);
        gos.close();
        return os.toByteArray().length;
    }

    public static void main(String[] args) throws IOException {
        String writeServerDns = args[0];

        InstanceInfoEncodingEfficiency tester = new InstanceInfoEncodingEfficiency(writeServerDns);
        tester.loadData();
        tester.instanceInfoEncoding();
    }
}
