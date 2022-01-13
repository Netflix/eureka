package com.netflix.appinfo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author David Liu
 */
public class AmazonInfoTest {
    @Test
    public void testExtractAccountId() throws Exception {
        String json = "{\n" +
                "  \"imageId\" : \"ami-someId\",\n" +
                "  \"instanceType\" : \"m1.small\",\n" +
                "  \"version\" : \"2000-00-00\",\n" +
                "  \"architecture\" : \"x86_64\",\n" +
                "  \"accountId\" : \"1111111111\",\n" +
                "  \"instanceId\" : \"i-someId\",\n" +
                "  \"billingProducts\" : null,\n" +
                "  \"pendingTime\" : \"2000-00-00T00:00:00Z\",\n" +
                "  \"availabilityZone\" : \"us-east-1c\",\n" +
                "  \"region\" : \"us-east-1\",\n" +
                "  \"kernelId\" : \"aki-someId\",\n" +
                "  \"ramdiskId\" : null,\n" +
                "  \"privateIp\" : \"1.1.1.1\"\n" +
                "}";

        InputStream inputStream = new ByteArrayInputStream(json.getBytes());
        String accountId = AmazonInfo.MetaDataKey.accountId.read(inputStream);

        assertEquals("1111111111", accountId);
    }
}
