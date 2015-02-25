package com.netflix.eureka2.server.health;

import javax.inject.Provider;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.netflix.eureka2.health.HealthStatusAggregator;
import com.netflix.eureka2.health.HealthStatusProvider;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * This module injects interceptors that collect all components implementing {@link HealthStatusProvider}
 * interface, excluding {@link HealthStatusAggregator}. The collected components are available via
 * {@link HealthStatusProviderRegistry} that should be injected in places where it is needed.
 *
 * @author Tomasz Bak
 */
public class EurekaHealthStatusModule extends AbstractModule {

    public static final Class[] EMPTY_PARAMETER_LIST = new Class[]{};

    private final HealthStatusProviderRegistry registry = new HealthStatusProviderRegistry();

    @Override
    protected void configure() {
        bind(HealthStatusProviderRegistry.class).toInstance(registry);
        bindInterceptor(new AbstractMatcher<Class<?>>() {
            @Override
            public boolean matches(Class<?> aClass) {
                if (Provider.class.isAssignableFrom(aClass)) {
                    Type type = ((ParameterizedType) aClass.getGenericInterfaces()[0]).getActualTypeArguments()[0];
                    if(type instanceof Class) {
                        Class<?> typeParameter = (Class<?>) type;
                        return isHealthStatusProvider(typeParameter);
                    }
                }
                return false;
            }
        }, new AbstractMatcher<Method>() {
            @Override
            public boolean matches(Method method) {
                return method.getName().equals("get") && method.getParameterTypes().length == 0;
            }
        }, new MethodInterceptor() {
            boolean nested;
            @Override
            public Object invoke(MethodInvocation invocation) throws Throwable {
                // For some reason the same invocation is passed twice via this interceptor
                if(nested) {
                    return invocation.proceed();
                }
                nested = true;
                try {
                    Object result = invocation.proceed();
                    registry.add((HealthStatusProvider) result);
                    return result;
                } finally {
                    nested = false;
                }
            }
        });
        bindListener(new AbstractMatcher<TypeLiteral<?>>() {
            @Override
            public boolean matches(TypeLiteral<?> typeLiteral) {
                return isHealthStatusProvider(typeLiteral.getRawType());
            }
        }, new TypeListener() {
            @Override
            public <T> void hear(TypeLiteral<T> type, TypeEncounter<T> encounter) {
                encounter.register(
                        new InjectionListener<T>() {
                            @Override
                            public void afterInjection(T healthStatusProvider) {
                                registry.add((HealthStatusProvider) healthStatusProvider);
                            }
                        });
            }
        });
    }

    private static boolean isHealthStatusProvider(Class<?> rawType) {
        return HealthStatusProvider.class.isAssignableFrom(rawType) && !HealthStatusAggregator.class.isAssignableFrom(rawType);
    }
}
