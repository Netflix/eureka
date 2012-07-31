package com.netflix.discovery.provider;

import java.lang.annotation.*; // import this to use @Documented

@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @ interface Serializer {
	String value() default "";  
}
