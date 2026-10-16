package com.ekp.rag;

import com.ekp.rag.ContextAssembler.Assembled;
import com.ekp.rag.ContextAssembler.Retrieved;
import com.ekp.rag.dto.SourceResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContextAssemblerTest {

    private Retrieved hit(String filename, int idx, String content, double sim) {
        return new Retrieved(UUID.randomUUID(), filename, idx, content, sim);
    }

    @Test
    void emptyInputProducesEmptyOutput() {
        Assembled out = ContextAssembler.assemble(List.of(), 1000);
        assertThat(out.context()).isEmpty();
        assertThat(out.sources()).isEmpty();
    }

    @Test
    void numbersAndFormatsSourcesSequentially() {
        Assembled out = ContextAssembler.assemble(List.of(
                hit("a.txt", 0, "alpha", 0.9),
                hit("b.txt", 3, "beta", 0.8)), 1000);

        assertThat(out.sources()).hasSize(2);
        assertThat(out.sources()).extracting(SourceResponse::number).containsExactly(1, 2);
        assertThat(out.context()).contains("[1] a.txt (chunk 0):", "alpha",
                "[2] b.txt (chunk 3):", "beta");
    }

    @Test
    void stopsAtCharacterBudget() {
        // Budget fits the first block but not the second.
        Assembled out = ContextAssembler.assemble(List.of(
                hit("a.txt", 0, "x".repeat(40), 0.9),
                hit("b.txt", 1, "y".repeat(40), 0.8)), 60);

        assertThat(out.sources()).hasSize(1);
        assertThat(out.context()).contains("a.txt").doesNotContain("b.txt");
    }

    @Test
    void truncatesAnOversizedFirstChunk() {
        Assembled out = ContextAssembler.assemble(List.of(
                hit("big.txt", 0, "z".repeat(500), 0.9)), 100);

        assertThat(out.sources()).hasSize(1);
        assertThat(out.context().length()).isLessThanOrEqualTo(100);
        // The snippet stored matches the (truncated) text placed in context.
        assertThat(out.sources().get(0).snippet().length()).isLessThan(500);
    }

    @Test
    void longCitationHeaderCannotExceedBudget() {
        Assembled out = ContextAssembler.assemble(List.of(hit("x".repeat(200),0,"evidence",0.9)),20);
        assertThat(out.context().length()).isLessThanOrEqualTo(20);
        assertThat(out.sources()).isEmpty();
    }

    @Test
    void zeroBudgetIncludesNoContextOrCitations() {
        Assembled out = ContextAssembler.assemble(List.of(hit("a.txt",0,"evidence",0.9)),0);
        assertThat(out.context()).isEmpty();
        assertThat(out.sources()).isEmpty();
    }

    @Test
    void negativeBudgetIsRejected() {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
            .isThrownBy(() -> ContextAssembler.assemble(List.of(),-1));
    }
}
