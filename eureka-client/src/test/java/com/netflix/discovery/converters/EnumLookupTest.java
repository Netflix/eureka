package com.netflix.discovery.converters;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EnumLookupTest {
    
    enum TestEnum {
        VAL_ONE("one"), VAL_TWO("two"), VAL_THREE("three");
        private final String name;
        
        private TestEnum(String name) {
            this.name = name;
        }
    }

    @Test
    public void testLookup() {
        EnumLookup<TestEnum> lookup = new EnumLookup<>(TestEnum.class, v->v.name.toCharArray());
        char[] buffer = "zeroonetwothreefour".toCharArray();
        Assertions.assertSame(TestEnum.VAL_ONE, lookup.find(buffer, 4, 3));
        Assertions.assertSame(TestEnum.VAL_TWO, lookup.find(buffer, 7, 3));
        Assertions.assertSame(TestEnum.VAL_THREE, lookup.find(buffer, 10, 5));
    }

}
