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
import com.netflix.eureka2.codec.CodecType;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.spi.protocol.registration.Register;
import com.netflix.eureka2.model.instance.InstanceInfo;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;

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

//    private final EurekaInterestClient interestClient;
//    private List<InstanceInfo> instanceInfos;
//
//    public InstanceInfoEncodingEfficiency(String writeServerDns) {
//        interestClient = Eurekas.newInterestClientBuilder().withServerResolver(
//                ServerResolvers.fromDnsName(writeServerDns).withPort(EurekaTransports.DEFAULT_DISCOVERY_PORT)
//        ).build();
//    }
//
//    public void loadData() {
//        instanceInfos = new ArrayList<>(
//                interestClient.forInterest(Interests.forFullRegistry())
//                        .compose(InterestFunctions.buffers())
//                        .compose(InterestFunctions.snapshots())
//                        .toBlocking().first()
//        );
//        System.out.printf("Loaded %d instances", instanceInfos.size());
//    }
//
//    public void instanceInfoEncoding() throws IOException {
//        System.out.println("Avro");
//        System.out.println("----------------------------");
//        instanceInfoEncoding(CodecType.Avro);
//
//        System.out.println("\nJSON");
//        System.out.println("----------------------------");
//        instanceInfoEncoding(CodecType.Json);
//    }
//
//    private void instanceInfoEncoding(CodecType codec) throws IOException {
//        EmbeddedChannel ch = new EmbeddedChannel(EurekaTransports.REGISTRATION_CODEC_FUNC.call(codec));
//
//        long total = 0;
//        long totalGzip = 0;
//
//        ByteArrayOutputStream totalBOS = new ByteArrayOutputStream();
//        GZIPOutputStream totalGOS = new GZIPOutputStream(totalBOS);
//
//        for (InstanceInfo data : instanceInfos) {
//            byte[] encoded = encode(ch, new Register(data));
//
//            total += encoded.length;
//            totalGzip += gzipSize(encoded);
//
//            totalBOS.write(encoded);
//        }
//        totalGOS.close();
//
//        System.out.println("Total uncompressed: " + total);
//        System.out.println("Total compressed: " + gzipSize(totalBOS.toByteArray()));
//        System.out.println("Average uncompressed: " + total / instanceInfos.size());
//        System.out.println("Average compressed: " + totalGzip / instanceInfos.size());
//    }
//
//    private static <T> byte[] encode(EmbeddedChannel ch, Register object) {
//        ch.writeOutbound(object);
//        ByteBuf byteBuf = (ByteBuf) ch.readOutbound();
//        int len = byteBuf.readableBytes();
//        byte[] result = new byte[len];
//        for (int i = 0; i < len; i++) {
//            result[i] = byteBuf.readByte();
//        }
//        return result;
//    }
//
//    private static int gzipSize(byte[] avroEncoded) throws IOException {
//        ByteArrayOutputStream os = new ByteArrayOutputStream();
//        GZIPOutputStream gos = new GZIPOutputStream(os);
//        gos.write(avroEncoded);
//        gos.close();
//        return os.toByteArray().length;
//    }
//
//    public static void main(String[] args) throws IOException {
//        String writeServerDns = args[0];
//
//        InstanceInfoEncodingEfficiency tester = new InstanceInfoEncodingEfficiency(writeServerDns);
//        tester.loadData();
//        tester.instanceInfoEncoding();
//    }
}
