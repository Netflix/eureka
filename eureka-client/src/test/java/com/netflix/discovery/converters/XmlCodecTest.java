package com.netflix.discovery.converters;

import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.InstanceInfoGenerator;
import com.thoughtworks.xstream.XStream;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class XmlCodecTest {

    @Test
    public void testEncodingDecodingWithoutMetaData() throws Exception {
        Applications applications = new InstanceInfoGenerator(10, 2, false).toApplications();

        XStream xstream = XmlXStream.getInstance();
        String xmlDocument = xstream.toXML(applications);

        Applications decodedApplications = (Applications) xstream.fromXML(xmlDocument);

        assertThat(EurekaEntityComparators.equal(decodedApplications, applications), is(true));
    }

    @Test
    public void testEncodingDecodingWithMetaData() throws Exception {
        Applications applications = new InstanceInfoGenerator(10, 2, true).toApplications();

        XStream xstream = XmlXStream.getInstance();
        String xmlDocument = xstream.toXML(applications);

        Applications decodedApplications = (Applications) xstream.fromXML(xmlDocument);

        assertThat(EurekaEntityComparators.equal(decodedApplications, applications), is(true));
    }
}
