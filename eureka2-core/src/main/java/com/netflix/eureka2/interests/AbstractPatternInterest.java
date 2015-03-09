package com.netflix.eureka2.interests;

import java.util.regex.Pattern;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractPatternInterest<T> extends Interest<T> {

    private final String pattern;
    private final Operator operator;

    private volatile Pattern compiledPattern;

    /* For serializer */
    protected AbstractPatternInterest() {
        pattern = null;
        operator = null;
    }

    protected AbstractPatternInterest(String pattern) {
        this(pattern, Operator.Equals);
    }

    protected AbstractPatternInterest(String pattern, Operator operator) {
        if (pattern == null) {
            throw new IllegalArgumentException("Expected non null pattern value");
        }
        if (operator == null) {
            throw new IllegalArgumentException("Expected non null operator value");
        }
        this.pattern = pattern;
        this.operator = operator;
    }

    public String getPattern() {
        return pattern;
    }

    public Operator getOperator() {
        return operator;
    }

    @Override
    public boolean matches(T data) {
        String value = getValue(data);
        if (value == null) {
            return false;
        }
        if (operator != Operator.Like) {
            return pattern.equals(value);
        }
        if (compiledPattern == null) {
            compiledPattern = Pattern.compile(pattern);
        }
        return compiledPattern.matcher(value).matches();
    }

    protected abstract String getValue(T data);

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        AbstractPatternInterest that = (AbstractPatternInterest) o;

        if (compiledPattern != null ? !compiledPattern.equals(that.compiledPattern) : that.compiledPattern != null)
            return false;
        if (operator != that.operator)
            return false;
        if (pattern != null ? !pattern.equals(that.pattern) : that.pattern != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = pattern != null ? pattern.hashCode() : 0;
        result = 31 * result + (operator != null ? operator.hashCode() : 0);
        result = 31 * result + (compiledPattern != null ? compiledPattern.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '{' +
                "pattern='" + pattern + '\'' +
                ", operator=" + operator +
                ", compiledPattern=" + compiledPattern +
                '}';
    }
}
