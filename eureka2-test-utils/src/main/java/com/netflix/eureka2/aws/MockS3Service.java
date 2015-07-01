package com.netflix.eureka2.aws;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A mock S3 implementation for testing S3 overrides
 *
 * @author David Liu
 */
public class MockS3Service {

    private final ConcurrentMap<String, Boolean> names;
    private final AmazonS3Client amazonS3Client;

    public MockS3Service() {
        this.names = new ConcurrentHashMap<>();
        this.amazonS3Client = mock(AmazonS3Client.class);
    }

    public Map<String, Boolean> getContentsView() {
        return Collections.unmodifiableMap(names);
    }

    public AmazonS3Client getAmazonS3Client() {
        setupMocks();
        return amazonS3Client;
    }

    protected void put(String name) {
        names.put(name, true);
    }

    protected void remove(String name) {
        names.remove(name);
    }

    public ObjectListing getListing() {
        ObjectListing listing = mock(ObjectListing.class);
        ArrayList<S3ObjectSummary> summaries = new ArrayList<>();
        for (String name : names.keySet()) {
            S3ObjectSummary summary = new S3ObjectSummary();
            summary.setKey(name);
            summaries.add(summary);
        }

        when(listing.getObjectSummaries()).thenReturn(summaries);
        when(listing.isTruncated()).thenReturn(false);
        when(listing.getNextMarker()).thenReturn(null);
        return listing;
    }

    // override to setup custom mocks
    protected void setupMocks() {
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                String name = invocation.getArguments()[1].toString();
                remove(name);
                return null;
            }
        }).when(amazonS3Client).deleteObject(anyString(), anyString());

        when(amazonS3Client.putObject(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                .thenAnswer(new Answer<PutObjectResult>() {
                    @Override
                    public PutObjectResult answer(InvocationOnMock invocation) throws Throwable {
                        String name = invocation.getArguments()[1].toString();
                        put(name);
                        return new PutObjectResult();
                    }
                });

        when(amazonS3Client.listObjects(any(ListObjectsRequest.class))).thenAnswer(new Answer<ObjectListing>() {
            @Override
            public ObjectListing answer(InvocationOnMock invocation) throws Throwable {
                ObjectListing listing = getListing();
                return listing;
            }
        });
    }

}
