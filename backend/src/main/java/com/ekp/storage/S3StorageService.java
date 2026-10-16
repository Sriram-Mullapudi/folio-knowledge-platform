package com.ekp.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class S3StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3;
    private final String bucket;

    public S3StorageService(S3Client s3, StorageProperties props) {
        this.s3 = s3;
        this.bucket = props.bucket();
    }

    @Override
    public void store(String key, byte[] bytes, String contentType) {
        try {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) bytes.length)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            throw new StorageException("Failed to store object " + key, e);
        }
    }

    @Override
    public StorageObject load(String key) {
        try {
            ResponseInputStream<GetObjectResponse> stream = s3.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            GetObjectResponse meta = stream.response();
            return new StorageObject(stream, meta.contentLength(), meta.contentType());
        } catch (S3Exception e) {
            throw new StorageException("Failed to load object " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception e) {
            throw new StorageException("Failed to delete object " + key, e);
        }
    }

    /**
     * Ensures the bucket exists. Used only when {@code auto-create-bucket} is on
     * (local dev). In the cloud the bucket is provisioned by infrastructure and
     * the app's credentials would not have bucket-creation rights.
     */
    void ensureBucketExists() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("Storage bucket '{}' is present", bucket);
        } catch (NoSuchBucketException e) {
            createBucket();
        } catch (S3Exception e) {
            // MinIO may surface a generic 404 rather than the typed exception.
            if (e.statusCode() == 404) {
                createBucket();
            } else {
                // Don't crash startup on a transient check failure; uploads will surface it.
                log.warn("Could not verify storage bucket '{}': {}", bucket, e.getMessage());
            }
        }
    }

    private void createBucket() {
        log.info("Creating storage bucket '{}'", bucket);
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception e) {
            // Another instance may have created it between the check and now.
            log.warn("Bucket create for '{}' did not succeed cleanly: {}", bucket, e.getMessage());
        }
    }
}
