package com.netflix.eureka.registry;


import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * abstract class with dummy method definitions for getting supported data types
 * for InstanceInfo.
 *
 * @author David Liu
 */
public abstract class InstanceInfoTypes {
    abstract String stringMethod();

    abstract Long longMethod();

    abstract TypeWrapper.HashSetString hashSetStringMethod();

    abstract TypeWrapper.HashSetInt hashSetIntMethod();

    abstract InstanceInfo.Status statusMethod();

    abstract DataCenterInfo instanceLocationMethod();

    public static final Set<Type> VALUE_TYPES;
    static {
        HashSet<Type> temp = new HashSet<Type>();
        for (Method method : InstanceInfoTypes.class.getDeclaredMethods()) {
            temp.add(method.getGenericReturnType());
        }
        VALUE_TYPES = Collections.unmodifiableSet(temp);
    }

}
