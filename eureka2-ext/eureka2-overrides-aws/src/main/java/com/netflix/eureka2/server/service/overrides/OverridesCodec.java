package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.codec.json.EurekaJsonCodec;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author David Liu
 */
public final class OverridesCodec {
    protected static final Class<?>[] OVERRIDES_MODEL = {OverridesDTO.class};
    protected static final Set<Class<?>> OVERRIDES_MODEL_SET = new HashSet<>(Arrays.asList(OVERRIDES_MODEL));
    protected static final EurekaJsonCodec<OverridesDTO> CODEC = new EurekaJsonCodec<>(OVERRIDES_MODEL_SET);

    public static EurekaJsonCodec<OverridesDTO> getCodec() {
        return CODEC;
    }
}
