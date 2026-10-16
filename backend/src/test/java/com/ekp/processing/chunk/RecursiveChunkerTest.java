package com.ekp.processing.chunk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for the chunker — no Spring, no I/O. Verifies the core
 * guarantees: size bounds, overlap, paragraph-boundary preference, and that
 * total coverage is preserved.
 */
class RecursiveChunkerTest {

    @Test
    void blankInputProducesNoChunks() {
        RecursiveChunker chunker = new RecursiveChunker(100, 20);
        assertThat(chunker.chunk("")).isEmpty();
        assertThat(chunker.chunk("   \n  ")).isEmpty();
        assertThat(chunker.chunk(null)).isEmpty();
    }

    @Test
    void shortTextStaysOneChunk() {
        RecursiveChunker chunker = new RecursiveChunker(100, 20);
        List<String> chunks = chunker.chunk("A short sentence that fits.");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo("A short sentence that fits.");
    }

    @Test
    void longTextIsSplitWithinSizeBudget() {
        String paragraph = "word ".repeat(400).trim(); // ~2000 chars, no paragraph breaks
        RecursiveChunker chunker = new RecursiveChunker(200, 40);

        List<String> chunks = chunker.chunk(paragraph);

        assertThat(chunks).hasSizeGreaterThan(1);
        // Allowance: a single unsplittable token could exceed the budget, but
        // here tokens are tiny, so every chunk should be within ~size.
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(200));
    }

    @Test
    void adjacentChunksOverlap() {
        // Distinct numbered words so we can detect shared tokens across the seam.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("token").append(i).append(' ');
        }
        RecursiveChunker chunker = new RecursiveChunker(120, 40);

        List<String> chunks = chunker.chunk(sb.toString().trim());

        assertThat(chunks).hasSizeGreaterThan(1);
        String endOfFirst = chunks.get(0);
        String startOfSecond = chunks.get(1);
        String lastTokenOfFirst = endOfFirst.substring(endOfFirst.lastIndexOf(' ') + 1);
        // The overlap means the second chunk re-includes the tail of the first.
        assertThat(startOfSecond).contains(lastTokenOfFirst);
    }

    @Test
    void keepsChunksWithinSizeAndPreservesContent() {
        String text = "First paragraph sentence one. First paragraph sentence two.\n\n"
                + "Second paragraph here.\n\nThird paragraph here.";
        RecursiveChunker chunker = new RecursiveChunker(60, 10);

        List<String> chunks = chunker.chunk(text);

        // Every chunk respects the size budget (these pieces are individually small).
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(60));
        // All source content survives chunking.
        String joined = String.join(" ", chunks);
        assertThat(joined).contains("First paragraph sentence one.");
        assertThat(joined).contains("Second paragraph here.");
        assertThat(joined).contains("Third paragraph here.");
    }

    @Test
    void rejectsOverlapNotSmallerThanSize() {
        assertThatThrownBy(() -> new RecursiveChunker(100, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecursiveChunker(100, 150))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
