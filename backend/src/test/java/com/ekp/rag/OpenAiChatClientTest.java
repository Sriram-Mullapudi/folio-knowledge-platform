package com.ekp.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extractsContentDelta() {
        String line = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}";
        assertThat(OpenAiChatClient.extractDelta(mapper, line)).contains("Hello");
    }

    @Test
    void ignoresDoneSentinel() {
        assertThat(OpenAiChatClient.extractDelta(mapper, "data: [DONE]")).isEmpty();
    }

    @Test
    void ignoresRoleOnlyFrame() {
        String line = "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}";
        assertThat(OpenAiChatClient.extractDelta(mapper, line)).isEmpty();
    }

    @Test
    void ignoresNonDataLinesAndBlanks() {
        assertThat(OpenAiChatClient.extractDelta(mapper, ": keep-alive")).isEmpty();
        assertThat(OpenAiChatClient.extractDelta(mapper, "")).isEmpty();
        assertThat(OpenAiChatClient.extractDelta(mapper, null)).isEmpty();
    }

    @Test
    void ignoresEmptyContent() {
        String line = "data: {\"choices\":[{\"delta\":{\"content\":\"\"}}]}";
        assertThat(OpenAiChatClient.extractDelta(mapper, line)).isEmpty();
    }
}
