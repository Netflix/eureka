package com.netflix.discovery.converters;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.thoughtworks.xstream.security.ForbiddenClassException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.EurekaEntityComparators;
import com.netflix.discovery.util.InstanceInfoGenerator;
import com.thoughtworks.xstream.XStream;

/**
 * @author Borja Lafuente
 */
public class JsonXStreamTest {

    @Test
    public void testEncodingDecodingWithoutMetaData() throws Exception {
        Applications applications = InstanceInfoGenerator.newBuilder(10, 2).withMetaData(false).build().toApplications();

        XStream xstream = JsonXStream.getInstance();
        String jsonDocument = xstream.toXML(applications);

        Applications decodedApplications = (Applications) xstream.fromXML(jsonDocument);

        assertThat(EurekaEntityComparators.equal(decodedApplications, applications), is(true));
    }

    @Test
    public void testEncodingDecodingWithMetaData() throws Exception {
        Applications applications = InstanceInfoGenerator.newBuilder(10, 2).withMetaData(true).build().toApplications();

        XStream xstream = JsonXStream.getInstance();
        String jsonDocument = xstream.toXML(applications);

        Applications decodedApplications = (Applications) xstream.fromXML(jsonDocument);

        assertThat(EurekaEntityComparators.equal(decodedApplications, applications), is(true));
    }

    /**
     * Tests: http://x-stream.github.io/CVE-2017-7957.html
     */
    @Test
    @Timeout(5000)
    public void testVoidElementUnmarshalling() throws Exception {
        assertThrows(ForbiddenClassException.class, () -> {
            XStream xstream = JsonXStream.getInstance();
            xstream.fromXML("{'void':null}");
        });
    }

}
