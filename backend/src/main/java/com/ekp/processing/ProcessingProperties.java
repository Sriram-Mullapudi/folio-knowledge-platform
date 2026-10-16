package com.ekp.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Async processing pool settings ({@code ekp.processing.*}). These size the
 * in-process work queue that runs extraction + chunking off the request thread.
 */
@ConfigurationProperties(prefix = "ekp.processing")
public record ProcessingProperties(
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity
) {}
