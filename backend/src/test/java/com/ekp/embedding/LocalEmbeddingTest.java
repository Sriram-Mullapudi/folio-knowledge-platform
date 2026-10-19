package com.ekp.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class LocalEmbeddingTest {
    EmbeddingProperties props(String url) {
        return new EmbeddingProperties(true,url,"","nomic-embed-text:v1.5",1536,8,0,0,
            Duration.ZERO,Duration.ZERO,Duration.ofSeconds(2),Duration.ofSeconds(5));
    }
    @Test void allowsKeylessOnlyForExplicitLocalModelEndpoint() {
        assertThat(props("http://127.0.0.1:11434/v1").isUsable()).isTrue();
        assertThat(props("https://example.com/v1").isUsable()).isFalse();
    }
    @Test void localRequestsOmitCredentialsAndDimensionOverrideAndPreserveVector() {
        var builder=RestClient.builder().baseUrl("http://127.0.0.1:11434/v1");
        var server=MockRestServiceServer.bindTo(builder).build();
        String vector="[1"+",0".repeat(767)+"]";
        server.expect(requestTo("http://127.0.0.1:11434/v1/embeddings"))
            .andExpect(headerDoesNotExist("Authorization"))
            .andExpect(content().json("{\"model\":\"nomic-embed-text:v1.5\",\"input\":[\"hello\"]}",true))
            .andRespond(withSuccess("{\"data\":[{\"index\":0,\"embedding\":"+vector+"}]}",MediaType.APPLICATION_JSON));
        var client=new OpenAiEmbeddingClient(builder.build(),props("http://127.0.0.1:11434/v1"),new EmbeddingRateLimiter(0));
        float[] result=client.embed(List.of("hello")).get(0);
        assertThat(result).hasSize(1536); assertThat(result[0]).isEqualTo(1);
        for(int i=1;i<1536;i++) assertThat(result[i]).isZero();
        server.verify();
    }
    @Test void rejectsWrongDimensionBeforePersistence() {
        var builder=RestClient.builder().baseUrl("http://127.0.0.1:11434/v1");
        var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://127.0.0.1:11434/v1/embeddings"))
            .andRespond(withSuccess("{\"data\":[{\"index\":0,\"embedding\":[1,2]}]}",MediaType.APPLICATION_JSON));
        var client=new OpenAiEmbeddingClient(builder.build(),props("http://127.0.0.1:11434/v1"),new EmbeddingRateLimiter(0));
        assertThatThrownBy(()->client.embed(List.of("hello"))).isInstanceOf(EmbeddingClient.EmbeddingException.class);
    }
}
