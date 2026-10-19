package com.ekp.processing.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Keeps recognized sections separate and repeats their actual headings on
 * continuation chunks so lexical search retains the section's identity. */
public final class SectionAwareChunker extends RecursiveChunker {
    private static final Pattern MARKDOWN = Pattern.compile("^(#{1,6})\\h+(.+)$");
    private static final Pattern LABEL = Pattern.compile("(?i)^(?:(?:professional|work)\\h+experience|experience|projects|education|certifications|summary|profile|(?:general|technical|professional)\\h+skills|skills|(?:project|policy)\\h*:.+):?$");
    private static final Pattern DATES = Pattern.compile("(?i).*\\b(?:19|20)\\d{2}\\h*[-–—]\\h*(?:(?:[a-z]+\\h+)?(?:19|20)\\d{2}|present|current)\\b.*");
    private final int size;
    private final int overlap;

    public SectionAwareChunker(int size, int overlap) {
        super(size, overlap);
        this.size=size;
        this.overlap=overlap;
    }

    @Override public List<String> chunk(String text) {
        if (text==null || text.isBlank()) return List.of();
        List<String> chunks=new ArrayList<>();
        List<String> parents=new ArrayList<>();
        String heading="";
        StringBuilder body=new StringBuilder();
        boolean found=false;
        for (String line : text.split("\\R", -1)) {
            String trimmed=line.strip();
            var markdown=MARKDOWN.matcher(trimmed);
            boolean md=markdown.matches();
            boolean plain=trimmed.length()<=Math.min(180,size/3)
                    && (LABEL.matcher(trimmed).matches() || DATES.matcher(trimmed).matches());
            if ((md || plain) && trimmed.length()<=Math.min(180,size/3)) {
                emit(chunks,heading,body.toString());
                body.setLength(0);
                found=true;
                if (md) {
                    int level=markdown.group(1).length();
                    while (parents.size()>=level) parents.remove(parents.size()-1);
                    while (parents.size()<level-1) parents.add("");
                    parents.add(trimmed);
                    heading=String.join("\n",parents.stream().filter(s->!s.isEmpty()).toList());
                    // Deep paths cannot consume the chunk budget.
                    if (heading.length()>size/2) heading=trimmed;
                } else {
                    parents.clear();
                    heading=trimmed;
                }
            } else {
                body.append(line).append('\n');
            }
        }
        if (!found) return super.chunk(text);
        emit(chunks,heading,body.toString());
        return List.copyOf(chunks);
    }

    private void emit(List<String> output, String heading, String text) {
        if (heading.isEmpty()) { output.addAll(super.chunk(text)); return; }
        if (text.isBlank()) { output.add(heading); return; }
        int budget=size-heading.length()-1;
        var splitter=new RecursiveChunker(budget,Math.min(overlap,budget-1));
        for (String piece : splitter.chunk(text)) output.add(heading+"\n"+piece);
    }
}
