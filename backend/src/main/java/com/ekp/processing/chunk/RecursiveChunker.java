package com.ekp.processing.chunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Recursive character text splitter with overlap.
 *
 * <p>It tries to split on the largest natural boundary first — paragraph, then
 * line, then sentence, then word, then (last resort) raw character — recursing
 * into any piece that is still larger than {@code chunkSize}. Adjacent small
 * pieces are then merged up to {@code chunkSize}, and each new chunk re-includes
 * the trailing {@code chunkOverlap} characters of the previous one so context
 * isn't severed at a boundary.
 *
 * <p>Sizes are measured in characters. As a rough rule of thumb ~4 characters ≈
 * 1 token for English, so a 1000-char chunk is ~250 tokens.
 *
 * <p>This mirrors the widely used "recursive character" strategy (e.g.
 * LangChain's {@code RecursiveCharacterTextSplitter}).
 */
public class RecursiveChunker {

    /** Boundaries from coarsest to finest. The empty string splits per-character. */
    private static final List<String> DEFAULT_SEPARATORS = List.of("\n\n", "\n", ". ", " ", "");

    private final int chunkSize;
    private final int chunkOverlap;
    private final List<String> separators;

    public RecursiveChunker(int chunkSize, int chunkOverlap) {
        this(chunkSize, chunkOverlap, DEFAULT_SEPARATORS);
    }

    public RecursiveChunker(int chunkSize, int chunkOverlap, List<String> separators) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("chunkOverlap must be >= 0 and < chunkSize");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.separators = separators;
    }

    /** Splits text into overlapping chunks. Returns an empty list for blank input. */
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return splitText(text, separators);
    }

    private List<String> splitText(String text, List<String> seps) {
        List<String> finalChunks = new ArrayList<>();

        // Choose the first separator that actually occurs (fall back to the last).
        String separator = seps.get(seps.size() - 1);
        List<String> remainingSeps = List.of();
        for (int i = 0; i < seps.size(); i++) {
            String candidate = seps.get(i);
            if (candidate.isEmpty()) {
                separator = candidate;
                break;
            }
            if (text.contains(candidate)) {
                separator = candidate;
                remainingSeps = seps.subList(i + 1, seps.size());
                break;
            }
        }

        List<String> splits = splitBySeparator(text, separator);

        // Pieces that fit are accumulated and merged; pieces that don't are recursed.
        List<String> goodSplits = new ArrayList<>();
        for (String piece : splits) {
            if (piece.length() < chunkSize) {
                goodSplits.add(piece);
                continue;
            }
            if (!goodSplits.isEmpty()) {
                finalChunks.addAll(mergeSplits(goodSplits, separator));
                goodSplits.clear();
            }
            if (remainingSeps.isEmpty()) {
                finalChunks.add(piece); // cannot split further
            } else {
                finalChunks.addAll(splitText(piece, remainingSeps));
            }
        }
        if (!goodSplits.isEmpty()) {
            finalChunks.addAll(mergeSplits(goodSplits, separator));
        }
        return finalChunks;
    }

    private List<String> splitBySeparator(String text, String separator) {
        List<String> parts = new ArrayList<>();
        if (separator.isEmpty()) {
            for (int i = 0; i < text.length(); i++) {
                parts.add(String.valueOf(text.charAt(i)));
            }
            return parts;
        }
        for (String part : text.split(Pattern.quote(separator), -1)) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts;
    }

    /**
     * Greedily packs pieces into chunks up to {@code chunkSize}, joined by
     * {@code separator}. When a chunk is emitted, pieces are dropped from the
     * front only until the retained tail is within {@code chunkOverlap}, so the
     * next chunk starts with that overlapping context.
     */
    private List<String> mergeSplits(List<String> splits, String separator) {
        int separatorLen = separator.length();
        List<String> chunks = new ArrayList<>();
        Deque<String> current = new ArrayDeque<>();
        int total = 0;

        for (String piece : splits) {
            int pieceLen = piece.length();
            int withSep = pieceLen + (current.isEmpty() ? 0 : separatorLen);

            if (total + withSep > chunkSize && !current.isEmpty()) {
                chunks.add(join(current, separator));
                // Shrink from the front until within the overlap budget (and within size).
                while (total > chunkOverlap
                        || (total + withSep > chunkSize && total > 0)) {
                    String first = current.peekFirst();
                    total -= first.length() + (current.size() > 1 ? separatorLen : 0);
                    current.pollFirst();
                    if (current.isEmpty()) {
                        break;
                    }
                }
            }

            current.addLast(piece);
            total += pieceLen + (current.size() > 1 ? separatorLen : 0);
        }

        if (!current.isEmpty()) {
            chunks.add(join(current, separator));
        }
        return chunks;
    }

    private String join(Deque<String> pieces, String separator) {
        return String.join(separator, pieces).strip();
    }
}
