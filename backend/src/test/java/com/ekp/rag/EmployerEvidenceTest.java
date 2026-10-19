package com.ekp.rag;

import com.ekp.embedding.ChunkVectorRepository.SearchHit;
import com.ekp.rag.dto.RagRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EmployerEvidenceTest {
    private SearchHit hit(String text) { return new SearchHit(UUID.randomUUID(),UUID.randomUUID(),0,text,0,.1); }

    @Test void removesGeneralSkillsInTheSameChunkAndUnrelatedChunks() {
        var role=hit("Birch Payments | Engineer | 2021–2023\nUsed Go, Redis, Kafka.\n\nGeneral skills\nRust, Kubernetes.");
        var result=EmployerEvidence.focus(new RagRequest("Which technologies did he use at Birch Payments?",null,null),
                List.of(role,hit("General skills\nJava, Python"),hit("Cedar Health\nReact")));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("Birch Payments | Engineer | 2021–2023\nUsed Go, Redis, Kafka.");
        assertThat(result.get(0).chunkId()).isEqualTo(role.chunkId());
    }

    @Test void usesTheEmployerFromQuestionHistoryButNotOldAnswerClaims() {
        var history=List.of(new RagRequest.PreviousTurn("What did he build at Cedar Health?","Used Rust at Birch Payments."));
        var result=EmployerEvidence.focus(new RagRequest("Which technologies did he use there?",null,null,null,history),
                List.of(hit("Cedar Health\nUsed Java.\nBirch Payments | Engineer | 2022\nUsed Rust.")));
        assertThat(result.get(0).content()).isEqualTo("Cedar Health\nUsed Java.");
    }

    @Test void generalSkillsQuestionDoesNotApplyEmployerFiltering() {
        var hits=List.of(hit("General skills: Rust"));
        assertThat(EmployerEvidence.focus(new RagRequest("Which technologies are listed?",null,null),hits)).isEqualTo(hits);
    }
}
