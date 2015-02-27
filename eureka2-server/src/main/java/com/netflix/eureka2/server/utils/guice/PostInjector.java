package com.netflix.eureka2.server.utils.guice;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A method annotated with {@link PostInjector} annotation will be executed after
 * injector is started. For it to work {@link PostInjectorModule} module has to be added
 * to the container.
 *
 * @author Tomasz Bak
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface PostInjector {
}
