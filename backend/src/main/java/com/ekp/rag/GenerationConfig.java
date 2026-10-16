package com.ekp.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.net.http.HttpClient;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Wires the RAG generation pipeline. The chat executor runs the per-request
 * streaming work off the request thread so the controller can return the
 * SseEmitter immediately; CallerRunsPolicy provides backpressure if the pool
 * and queue saturate.
 */
@Configuration
@EnableConfigurationProperties(GenerationProperties.class)
public class GenerationConfig {

    public static final String EXECUTOR = "chatExecutor";

    @Bean
    public HttpClient chatHttpClient(GenerationProperties props) {
        return HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .build();
    }

    @Bean
    public ChatModelClient chatModelClient(HttpClient chatHttpClient, GenerationProperties props,
                                           ObjectMapper mapper) {
        return new OpenAiChatClient(chatHttpClient, props, mapper);
    }

    @Bean(EXECUTOR)
    public Executor chatExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chat-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
