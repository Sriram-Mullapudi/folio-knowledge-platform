package com.ekp.embedding;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Wires the embedding pipeline. The executor is intentionally small: request
 * rate is governed by {@link EmbeddingRateLimiter}, so adding threads wouldn't
 * raise throughput, only contention.
 */
@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingConfig {

    public static final String EXECUTOR = "embeddingExecutor";

    @Bean
    public EmbeddingRateLimiter embeddingRateLimiter(EmbeddingProperties props) {
        return new EmbeddingRateLimiter(props.requestsPerMinute());
    }

    @Bean
    public RestClient embeddingRestClient(EmbeddingProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Bean
    public EmbeddingClient embeddingClient(RestClient embeddingRestClient, EmbeddingProperties props,
                                           EmbeddingRateLimiter rateLimiter) {
        return new OpenAiEmbeddingClient(embeddingRestClient, props, rateLimiter);
    }

    @Bean(EXECUTOR)
    public Executor embeddingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("embed-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
