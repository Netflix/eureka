package com.netflix.eureka2.junit.categories;

/**
 * A marker for tests, that make calls to external services. An example
 * would be a DNS resolver. The set of tests of this kind should be minimal, and
 * limited to the connector layer only. These tests are not run as part
 * of the main build process, to avoid unpredictable failures when the external
 * dependencies are not available.
 *
 * @author Tomasz Bak
 */
public interface ExternalConnectionTest {
}
