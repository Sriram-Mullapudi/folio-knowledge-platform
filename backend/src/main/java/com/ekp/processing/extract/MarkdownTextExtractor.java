package com.ekp.processing.extract;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Parses Markdown to its AST and renders the text content, stripping syntax
 * (heading markers, emphasis, link URLs, list bullets) while preserving the
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
        return renderer.render(document);
    }
}
