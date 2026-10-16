package com.ekp.rag;

import com.ekp.rag.dto.SourceResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Assembles retrieved chunks into a numbered context block within a character
 * budget, and produces the matching {@link SourceResponse} citations. Pure and
 * deterministic (no I/O) so it can be unit-tested directly.
 *
 * <p>Chunks are consumed in retrieval order (most similar first). Numbering is
 * sequential starting at 1 so the model's inline [n] markers line up with the
 * source list shown to the user.
 */
public final class ContextAssembler {

    private ContextAssembler() {
    }

    /** A retrieved chunk enriched with its document's display name. */
    public record Retrieved(UUID documentId, String filename, int chunkIndex,
                            String content, double similarity) {}

    /** The formatted context plus the citations actually included in it. */
    public record Assembled(String context, List<SourceResponse> sources) {}

    public static Assembled assemble(List<Retrieved> hits, int maxContextChars) {
        if (maxContextChars < 0) throw new IllegalArgumentException("Context budget must not be negative");
        List<SourceResponse> sources = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        int used = 0;
        int number = 0;

        for (Retrieved hit : hits) {
            int candidateNumber = number + 1;
            String header = "[" + candidateNumber + "] " + hit.filename()
                    + " (chunk " + hit.chunkIndex() + "):\n";
            if (header.length() + 2 >= maxContextChars - used) break;
            String content = hit.content();
            String block = header + content + "\n\n";

            if (used == 0 && block.length() > maxContextChars) {
                // First chunk alone exceeds the budget: include it, truncated.
                int allowed = Math.max(0, maxContextChars - header.length() - 2);
                content = content.substring(0, Math.min(content.length(), allowed));
                block = header + content + "\n\n";
            } else if (used > 0 && used + block.length() > maxContextChars) {
                break; // budget reached; drop this and any remaining chunks
            }

            context.append(block);
            used += block.length();
            number = candidateNumber;
            sources.add(new SourceResponse(number, hit.documentId(), hit.filename(),
                    hit.chunkIndex(), hit.similarity(), content));
        }

        return new Assembled(context.toString().strip(), List.copyOf(sources));
    }
}
