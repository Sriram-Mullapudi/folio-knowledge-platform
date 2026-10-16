package com.ekp.processing;

import com.ekp.document.DocumentProperties;
import com.ekp.processing.chunk.RecursiveChunker;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Wires the asynchronous document-processing pipeline.
 *
 * <p>The executor <em>is</em> the processing queue: a bounded thread pool with a
 * bounded backlog. When the backlog is full we fall back to
 * {@link ThreadPoolExecutor.CallerRunsPolicy}, which makes the submitting thread
 * run the task — natural backpressure that prevents unbounded memory growth.
 *
 * <p>This is a single-node queue, which is the right complexity for now. To
 * scale across instances or survive a restart mid-task, the same seam (enqueue →
 * worker) swaps to Redis/SQS without touching the pipeline logic; document
 * status in Postgres already provides durable state and crash recovery.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(ProcessingProperties.class)
public class ProcessingConfig {

    public static final String EXECUTOR = "documentProcessingExecutor";

    @Bean(EXECUTOR)
    public Executor documentProcessingExecutor(ProcessingProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.corePoolSize());
        executor.setMaxPoolSize(props.maxPoolSize());
        executor.setQueueCapacity(props.queueCapacity());
        executor.setThreadNamePrefix("doc-proc-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    public RecursiveChunker recursiveChunker(DocumentProperties props) {
        return new RecursiveChunker(props.chunkSize(), props.chunkOverlap());
    }
}
