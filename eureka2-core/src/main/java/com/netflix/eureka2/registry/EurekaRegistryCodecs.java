package com.netflix.eureka2.registry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * Provides encoders/decoders for Eureka registry data persistence. Its primary usage is
 * backup registry generation/loading.
 * <h3>Multiple version support</h3>
 * Not provided yet.
 *
 * @author Tomasz Bak
 */
public class EurekaRegistryCodecs {

    public interface Codec<T> {
        void encode(T data, OutputStream output) throws IOException;

        T decode(InputStream input) throws IOException;
    }

    public static <C> Codec<C> codec(Class<C> aClass) {
        if (!aClass.isAssignableFrom(ChangeNotification.class)) {
            throw new IllegalArgumentException("No codec available for type " + aClass);
        }
        return ChangeNotificationCodec.INSTANCE;
    }

    static class ChangeNotificationCodec implements Codec<ChangeNotification<InstanceInfo>> {

        static Codec INSTANCE = new ChangeNotificationCodec();

        @Override
        public void encode(ChangeNotification<InstanceInfo> data, OutputStream output) throws IOException {

        }

        @Override
        public ChangeNotification<InstanceInfo> decode(InputStream input) throws IOException {
            return null;
        }
    }
}
