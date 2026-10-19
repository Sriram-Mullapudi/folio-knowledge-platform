package com.ekp.rag;

import com.ekp.embedding.ChunkVectorRepository.SearchHit;
import com.ekp.rag.dto.RagRequest;
import java.util.List;
import java.util.regex.Pattern;

/** Conservative excerpt selection for explicitly named employer technology questions.
 * Does not infer employers from generated answers or invent section metadata. */
final class EmployerEvidence {
    private static final Pattern TECHNOLOGY = Pattern.compile("(?i)\\b(technologies|technology|tools|stack)\\b");
    private static final Pattern EMPLOYER = Pattern.compile("\\b(?:at|for)\\s+([\\p{Lu}][\\p{L}\\p{N}&.-]*(?:\\s+[\\p{Lu}][\\p{L}\\p{N}&.-]*){0,4})(?=\\s*[?!]|$)");
    private static final Pattern BOUNDARY = Pattern.compile("(?im)^\\h*(?:(?:(?:general|technical|professional)\\h+)?(?:skills|education|certifications|summary|profile)\\h*:?\\h*$|[^\\r\\n]+\\|[^\\r\\n]*\\b(?:19|20)\\d{2}\\b)");

    static List<SearchHit> focus(RagRequest request, List<SearchHit> hits) {
        if (!isTechnologyQuestion(request.question())) return hits;
        String employer = employer(request.question());
        // Only consult history when the current question is referential.
        if (employer == null && Pattern.compile("(?i)\\b(there|he|she|his|her|they|their|it|its)\\b").matcher(request.question()).find()
                && request.history() != null) {
            for (int i=request.history().size()-1; i>=0 && employer==null; i--) employer=employer(request.history().get(i).question());
        }
        if (employer == null) return hits;
        Pattern name=Pattern.compile("(?i)(?<![\\p{L}\\p{N}])"+Pattern.quote(employer)+"(?![\\p{L}\\p{N}])");
        return hits.stream().map(hit -> {
            var match=name.matcher(hit.content());
            if (!match.find()) return null;
            // Keep only literal source text starting with the named role. General
            // summary/skills chunks without the employer are deliberately excluded.
            String excerpt=hit.content().substring(match.start());
            int newline=excerpt.indexOf('\n');
            if (newline>=0) {
                var boundary=BOUNDARY.matcher(excerpt);
                if (boundary.find(newline+1)) excerpt=excerpt.substring(0,boundary.start()).strip();
            }
            return new SearchHit(hit.chunkId(),hit.documentId(),hit.chunkIndex(),excerpt,hit.distance(),hit.similarity());
        }).filter(java.util.Objects::nonNull).toList();
    }

    static boolean isTechnologyQuestion(String question) { return TECHNOLOGY.matcher(question).find(); }

    private static String employer(String question) {
        var match=EMPLOYER.matcher(question);
        return match.find() ? match.group(1).strip() : null;
    }
}
