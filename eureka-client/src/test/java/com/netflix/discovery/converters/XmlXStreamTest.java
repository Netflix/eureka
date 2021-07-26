package com.netflix.discovery.converters;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.EurekaEntityComparators;
import com.netflix.discovery.util.InstanceInfoGenerator;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.ForbiddenClassException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * @author Tomasz Bak
 */
public class XmlXStreamTest {

    @Test
    public void testEncodingDecodingWithoutMetaData() throws Exception {
        Applications applications = InstanceInfoGenerator.newBuilder(10, 2).withMetaData(false).build().toApplications();

        XStream xstream = XmlXStream.getInstance();
        String xmlDocument = xstream.toXML(applications);

        Applications decodedApplications = (Applications) xstream.fromXML(xmlDocument);

        assertThat(EurekaEntityComparators.equal(decodedApplications, applications), is(true));
    }

    @Test
    public void testEncodingDecodingWithMetaData() throws Exception {
        Applications applications = InstanceInfoGenerator.newBuilder(10, 2).withMetaData(true).build().toApplications();

        XStream xstream = XmlXStream.getInstance();
        String xmlDocument = xstream.toXML(applications);

        Applications decodedApplications = (Applications) xstream.fromXML(xmlDocument);

        assertThat(EurekaEntityComparators.equal(decodedApplications, applications), is(true));
    }

    /**
     * Tests: http://x-stream.github.io/CVE-2017-7957.html
     */
    @Test
    @Timeout(5000)
    public void testVoidElementUnmarshalling() throws Exception {
        assertThrows(ForbiddenClassException.class, () -> {
            XStream xstream = XmlXStream.getInstance();
            xstream.fromXML("<void/>");
        });
    }

    /**
     * Tests: http://x-stream.github.io/CVE-2017-7957.html
     */
    @Test
    @Timeout(5000)
    public void testVoidAttributeUnmarshalling() throws Exception {
        assertThrows(ForbiddenClassException.class, () -> {
            XStream xstream = XmlXStream.getInstance();
            xstream.fromXML("<string class='void'>Hello, world!</string>");
        });
    }
}
