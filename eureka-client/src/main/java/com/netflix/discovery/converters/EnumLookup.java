package com.netflix.discovery.converters;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * utility class for matching a Enum value to a region of a char[] without
 * allocating any new objects on the heap.
 */
public class EnumLookup<T extends Enum<T>> {
    private final int[] sortedHashes;
    private final char[][] sortedNames;
    private final Map<String, T> stringLookup;
    private final T[] sortedValues;
    private final int minLength;
    private final int maxLength;

    EnumLookup(Class<T> enumType) {
        this(enumType, t -> t.name().toCharArray());
    }

    @SuppressWarnings("unchecked")
    EnumLookup(Class<T> enumType, Function<T, char[]> namer) {
        this.sortedValues = (T[]) Array.newInstance(enumType, enumType.getEnumConstants().length);
        System.arraycopy(enumType.getEnumConstants(), 0, sortedValues, 0, sortedValues.length);
        Arrays.sort(sortedValues,
                (o1, o2) -> Integer.compare(Arrays.hashCode(namer.apply(o1)), Arrays.hashCode(namer.apply(o2))));

        this.sortedHashes = new int[sortedValues.length];
        this.sortedNames = new char[sortedValues.length][];
        int i = 0;
        int minLength = Integer.MAX_VALUE;
        int maxLength = Integer.MIN_VALUE;
        stringLookup = new HashMap<>();
        for (T te : sortedValues) {
            char[] name = namer.apply(te);
            int hash = Arrays.hashCode(name);
            sortedNames[i] = name;
            sortedHashes[i++] = hash;
            stringLookup.put(String.valueOf(name), te);
            maxLength = Math.max(maxLength, name.length);
            minLength = Math.min(minLength, name.length);
        }
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    public T find(JsonParser jp) throws IOException {
        return find(jp, null);
    }

    public T find(JsonParser jp, T defaultValue) throws IOException {
        if (jp.getCurrentToken() == JsonToken.FIELD_NAME) {
            return stringLookup.getOrDefault(jp.getCurrentName(), defaultValue);
        }
        return find(jp.getTextCharacters(), jp.getTextOffset(), jp.getTextLength(), defaultValue);
    }

    public T find(char[] a, int offset, int length) {
        return find(a, offset, length, null);
    }

    public T find(char[] a, int offset, int length, T defaultValue) {
        if (length < this.minLength || length > this.maxLength) return defaultValue;
        
        int hash = hashCode(a, offset, length);
        int index = Arrays.binarySearch(sortedHashes, hash);
        if (index >= 0) {
            for (int i = index; i < sortedValues.length && sortedHashes[index] == hash; i++) {
                if (equals(sortedNames[i], a, offset, length)) {
                    return sortedValues[i];
                }
            }
        }
        return defaultValue;
    }

    public static boolean equals(char[] a1, char[] a2, int a2Offset, int a2Length) {
        if (a1.length != a2Length)
            return false;
        for (int i = 0; i < a2Length; i++) {
            if (a1[i] != a2[i + a2Offset])
                return false;
        }
        return true;
    }

    public static int hashCode(char[] a, int offset, int length) {
        if (a == null)
            return 0;
        int result = 1;
        for (int i = 0; i < length; i++) {
            result = 31 * result + a[i + offset];
        }
        return result;
    }
}