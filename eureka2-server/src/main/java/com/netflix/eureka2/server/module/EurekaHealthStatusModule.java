package com.netflix.eureka2.server.module;

import javax.inject.Provider;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.netflix.eureka2.health.EurekaHealthStatusAggregator;
import com.netflix.eureka2.health.HealthStatusProvider;
import com.netflix.eureka2.server.health.HealthStatusProviderRegistry;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * This module injects interceptors that collect all components implementing {@link HealthStatusProvider}
 * interface, excluding {@link EurekaHealthStatusAggregator}. The collected components are available via
 * {@link com.netflix.eureka2.server.health.HealthStatusProviderRegistry} that should be injected in places where it is needed.
 *
 * @author Tomasz Bak
 */
public class EurekaHealthStatusModule extends AbstractModule {

    private final HealthStatusProviderRegistry.ProviderHolder holder = new HealthStatusProviderRegistry.ProviderHolder();

    @Override
    protected void configure() {
        bind(HealthStatusProviderRegistry.ProviderHolder.class).toInstance(holder);
        bind(HealthStatusProviderRegistry.class).in(Scopes.SINGLETON);

        bindInterceptor(new AbstractMatcher<Class<?>>() {
            @Override
            public boolean matches(Class<?> aClass) {
                if (Provider.class.isAssignableFrom(aClass)) {
                    Class<?> baseGeneric = aClass;
                    while (baseGeneric.getGenericInterfaces().length == 0) {
                        baseGeneric = (Class<?>) baseGeneric.getGenericSuperclass();
                    }

                    Type type = ((ParameterizedType) baseGeneric.getGenericInterfaces()[0]).getActualTypeArguments()[0];
                    if (type instanceof Class) {
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
                if (nested) {
                    return invocation.proceed();
                }
                nested = true;
                try {
                    Object result = invocation.proceed();
                    holder.add((HealthStatusProvider) result);
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
                                holder.add((HealthStatusProvider) healthStatusProvider);
                            }
                        });
            }
        });
    }

    private static boolean isHealthStatusProvider(Class<?> rawType) {
        return HealthStatusProvider.class.isAssignableFrom(rawType) && !EurekaHealthStatusAggregator.class.isAssignableFrom(rawType);
    }
}
