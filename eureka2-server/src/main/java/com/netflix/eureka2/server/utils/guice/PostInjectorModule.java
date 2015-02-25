package com.netflix.eureka2.server.utils.guice;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.governator.guice.PostInjectorAction;

/**
 * Track components with {@link PostInjector} annotated method, and execute those
 * methods after the container is started.
 *
 * @author Tomasz Bak
 */
public class PostInjectorModule extends AbstractModule {

    private final List<Runnable> postInjectActions = new ArrayList<>();

    protected PostInjectorModule(LifecycleInjectorBuilder lifecycleInjectorBuilder) {
        lifecycleInjectorBuilder.withPostInjectorAction(new PostInjectorAction() {
            @Override
            public void call(Injector injector) {
                for (Runnable r : postInjectActions) {
                    r.run();
                }
            }
        });
    }

    @Override
    protected void configure() {
        bindListener(Matchers.any(), new TypeListener() {
            @Override
            public <T> void hear(TypeLiteral<T> type, TypeEncounter<T> encounter) {
                encounter.register(
                        new InjectionListener<T>() {
                            @Override
                            public void afterInjection(T object) {
                                addIfPostInjectorAnnotated(object);
                            }
                        });
            }
        });
    }

    private <T> void addIfPostInjectorAnnotated(final T object) {
        for (final Method method : object.getClass().getMethods()) {
            if (method.getAnnotation(PostInjector.class) != null) {
                postInjectActions.add(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            method.invoke(object);
                        } catch (IllegalAccessException e) {
                            throw new IllegalStateException("Post inject action failure", e);
                        } catch (InvocationTargetException e) {
                            throw new IllegalStateException("Post inject action failure", e);
                        }
                    }
                });
            }
        }
    }

    public static PostInjectorModule forLifecycleInjectorBuilder(LifecycleInjectorBuilder lifecycleInjectorBuilder) {
        return new PostInjectorModule(lifecycleInjectorBuilder);
    }
}
