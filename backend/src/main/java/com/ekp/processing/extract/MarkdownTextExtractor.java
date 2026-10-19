package com.ekp.processing.extract;

import org.commonmark.node.Node;
import org.commonmark.node.Heading;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Parses Markdown to its AST and renders the text content, stripping syntax
 * (emphasis, link URLs, list bullets) while preserving heading levels and the
 * human-readable prose. This keeps chunks free of markup noise that would
 * otherwise pollute embeddings and retrieval.
 */
@Component
public class MarkdownTextExtractor implements TextExtractor {

    private final Parser parser = Parser.builder().build();
    private final TextContentRenderer renderer = TextContentRenderer.builder().build();

    @Override
    public String contentType() {
        return "text/markdown";
    }

    @Override
    public String extract(byte[] bytes) {
        Node document = parser.parse(new String(bytes, StandardCharsets.UTF_8));
        StringBuilder text = new StringBuilder();
        for (Node node=document.getFirstChild(); node!=null; node=node.getNext()) {
            if (node instanceof Heading heading) text.append("#".repeat(heading.getLevel())).append(' ');
            text.append(renderer.render(node).strip()).append("\n\n");
        }
        return text.toString().strip();
    }
}
