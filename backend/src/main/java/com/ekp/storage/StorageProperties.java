package com.ekp.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3-compatible object storage settings ({@code ekp.storage.s3.*}). The same
 * code targets MinIO locally and AWS S3 in the cloud — only these values change.
 *
 * @param endpoint        service endpoint (MinIO URL locally; omit/AWS URL in prod)
 * @param region          AWS region (any value for MinIO, e.g. us-east-1)
 * @param bucket          bucket that holds uploaded documents
 * @param accessKey       access key / MinIO root user
 * @param secretKey       secret key / MinIO root password
 * @param pathStyleAccess MinIO requires path-style addressing; S3 supports it too
 * @param autoCreateBucket create the bucket on startup if missing (dev convenience)
 */
@ConfigurationProperties(prefix = "ekp.storage.s3")
public record StorageProperties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyleAccess,
        boolean autoCreateBucket
) {}
