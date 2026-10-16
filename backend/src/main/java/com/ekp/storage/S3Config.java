package com.ekp.storage;

import com.ekp.document.DocumentProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Builds the S3 client for MinIO/S3 and (optionally) ensures the bucket exists.
 *
 * <p>Path-style access is required by MinIO and supported by S3, so we enable it
 * to keep one configuration for both. Credentials are static here; in AWS you
 * would drop the explicit keys and let the default provider chain use the
 * instance/task IAM role.
 */
@Configuration
@EnableConfigurationProperties({StorageProperties.class, DocumentProperties.class})
public class S3Config {

    @Bean
    public S3Client s3Client(StorageProperties props) {
        return S3Client.builder()
                .region(Region.of(props.region()))
                .endpointOverride(URI.create(props.endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.pathStyleAccess())
                        .build())
                .build();
    }

    /**
     * Creates the bucket on startup when enabled. Guarded by the property so it
     * is disabled in tests/production where the bucket is managed externally.
     */
    @Bean
    public ApplicationRunner bucketInitializer(StorageProperties props, S3StorageService storage) {
        return (ApplicationArguments args) -> {
            if (props.autoCreateBucket()) {
                storage.ensureBucketExists();
            }
        };
    }
}
