package com.ekp.rag;

/** A single chat message in the OpenAI roles vocabulary (system|user|assistant). */
public record ChatMessage(String role, String content) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }
}
