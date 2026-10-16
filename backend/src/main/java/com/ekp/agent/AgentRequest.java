package com.ekp.agent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
public record AgentRequest(@NotBlank @Size(max=4000) String question, Mode mode, UUID documentId, UUID collectionId) {
    public AgentRequest(String question,Mode mode,UUID documentId){this(question,mode,documentId,null);}
    public enum Mode { AUTO, RETRIEVAL, RESEARCH, SUMMARIZATION, METADATA }
}
