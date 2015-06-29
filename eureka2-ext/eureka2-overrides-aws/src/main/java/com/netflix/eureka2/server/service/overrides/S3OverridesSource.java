package com.netflix.eureka2.server.service.overrides;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.netflix.eureka2.codec.json.EurekaJsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * An s3 backed source for storing overrides in individual json files. This class is NOT optimized.
 *
 * FIXME: fix the deserializer for polymorphic deser of delta collections.
 *
 * @author David Liu
 */
@Singleton
public class S3OverridesSource implements LoadingOverridesRegistry.ExternalOverridesSource {

    private static final Logger logger = LoggerFactory.getLogger(S3OverridesSource.class);

    private final S3OverridesConfig config;
    private final AmazonS3Client s3Client;
    private final EurekaJsonCodec<OverridesDTO> codec;

    @Inject
    public S3OverridesSource(S3OverridesConfig config) {
        this(config, new AmazonS3Client());
    }

    public S3OverridesSource(S3OverridesConfig config, AmazonS3Client s3Client) {
        this.config = config;
        this.s3Client = s3Client;
        this.codec = OverridesCodec.getCodec();
    }

    @Override
    public void set(Overrides overrides) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        codec.encode(OverridesDTO.fromOverrides(overrides), outputStream);
        InputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(outputStream.size());

        PutObjectResult result = s3Client.putObject(
                config.getBucketName(),
                toS3Name(config.getPrefix(), overrides.getId()),
                inputStream,
                metadata
        );

        logger.debug("S3 put result: {}", result);
    }

    @Override
    public void remove(String id) throws Exception {
        s3Client.deleteObject(config.getBucketName(), toS3Name(config.getPrefix(), id));
    }

    // TODO: add metrics on timing for this
    @Override
    public Map<String, Overrides> asMap() {
        Map<String, Overrides> result = new HashMap<>();

        ListObjectsRequest request = new ListObjectsRequest()
                .withBucketName(config.getBucketName())
                .withPrefix(config.getPrefix());

        ObjectListing listing;
        do {
            listing = s3Client.listObjects(request);
            for (S3ObjectSummary summary : listing.getObjectSummaries()) {
                Overrides overrides = readOverrides(summary);
                if (overrides != null) {
                    result.put(overrides.getId(), overrides);
                }
            }
            request.setMarker(listing.getNextMarker());
        } while (listing.isTruncated());

        return result;
    }

    private Overrides readOverrides(S3ObjectSummary summary) {
        try {
            S3Object s3Object = s3Client.getObject(summary.getBucketName(), summary.getKey());
            BufferedReader reader = new BufferedReader(new InputStreamReader(s3Object.getObjectContent()));
            String line = reader.readLine();
            while ( line != null) {
                System.out.println(line);
                line = reader.readLine();
            }

            Overrides overrides = codec.decode(s3Object.getObjectContent()).toOverrides();
            logger.debug("Read override from s3: {}", overrides);
            return overrides;
        } catch (Exception e) {
            logger.warn("Error reading override from s3", e);
            return null;
        }
    }

    private String toS3Name(String name, String id) {
        return name + "/" + id;
    }
}
