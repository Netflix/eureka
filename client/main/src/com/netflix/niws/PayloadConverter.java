package com.netflix.niws;

import java.lang.annotation.*; // import this to use @Documented

@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @ interface PayloadConverter {
	String value() default "com.netflix.niws.XStreamPayloadConverter";  
}
