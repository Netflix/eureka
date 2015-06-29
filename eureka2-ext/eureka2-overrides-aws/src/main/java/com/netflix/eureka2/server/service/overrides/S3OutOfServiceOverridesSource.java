package com.netflix.eureka2.server.service.overrides;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.netflix.eureka2.registry.instance.Delta;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfoField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An optimized s3 backed overrides source for OUT_OF_SERVICE status overrides only.
 * In most cases overrides are only needed for this specific case, and given this constraint,
 * we can actually make use of s3 object names for storage.
 *
 * The file format will be of the form: {config.getPrefix()}/overrideId
 * where if the file is present, then the OOS override exist
 *
 * @author David Liu
 */
@Singleton
public class S3OutOfServiceOverridesSource implements LoadingOverridesRegistry.ExternalOverridesSource {

    private static final Logger logger = LoggerFactory.getLogger(S3OutOfServiceOverridesSource.class);

    private final S3OverridesConfig config;
    private final AmazonS3Client s3Client;

    @Inject
    public S3OutOfServiceOverridesSource(S3OverridesConfig config) {
        this(config, new AmazonS3Client());
    }

    public S3OutOfServiceOverridesSource(S3OverridesConfig config, AmazonS3Client s3Client) {
        this.config = config;
        this.s3Client = s3Client;
    }

    @Override
    public void set(Overrides overrides) throws Exception {
        if (overrides.getDeltas().size() != 1) {
            throw new UnsupportedOperationException("Not handling multiple overrides");
        }

        Delta<?> delta = overrides.getDeltas().iterator().next();
        if (delta.getField().getValueType() != InstanceInfo.Status.class) {
            throw new UnsupportedOperationException("Not handling non-status overrides");
        }

        InstanceInfo.Status status = (InstanceInfo.Status) delta.getValue();
        if (status != InstanceInfo.Status.OUT_OF_SERVICE) {
            throw new UnsupportedOperationException("Not handling non OUT_OR_SERVICE overrides");
        }

        InputStream inputStream = new ByteArrayInputStream(new byte[0]);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(0);

        String name = toS3Name(config.getPrefix(), overrides.getId());

        PutObjectResult result = s3Client.putObject(
                config.getBucketName(),
                name,
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
        String id = fromS3Name(summary.getKey());
        if (id != null) {
            Delta<?> delta = new Delta.Builder()
                    .withId(id)
                    .withDelta(InstanceInfoField.STATUS, InstanceInfo.Status.OUT_OF_SERVICE)
                    .build();

            Set<Delta<?>> deltas = new HashSet<>();
            deltas.add(delta);

            return new Overrides(id, deltas);
        }

        return null;
    }

    private String toS3Name(String name, String id) {
        return name + "/" + id;
    }

    private String fromS3Name(String s3Name) {
        String[] parts = s3Name.split("/");
        if (parts.length == 2) {
            return parts[1];
        }
        return null;
    }
}
