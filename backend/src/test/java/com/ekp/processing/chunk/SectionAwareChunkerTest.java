package com.ekp.processing.chunk;

import com.ekp.processing.extract.MarkdownTextExtractor;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SectionAwareChunkerTest {
    @Test void continuationRetainsEmployerWithoutLeakingTheNextRoleOrSkills() {
        var chunker=new SectionAwareChunker(300,40);
        String text="Cedar Health | Engineer | 2024–Present\n"
                + "Built scheduling services with Java. ".repeat(25)
                + "Late detail: used CobaltQueue for notifications.\n"
                + "Birch Payments | Engineer | 2021–2023\nUsed Go and Kafka.\nGeneral skills\nRust and Terraform.";
        var chunks=chunker.chunk(text);
        assertThat(chunks).allMatch(s->s.length()<=300);
        assertThat(chunks.stream().filter(s->s.contains("CobaltQueue"))).allMatch(s->s.startsWith("Cedar Health"));
        assertThat(chunks).anyMatch(s->s.contains("CobaltQueue"));
        assertThat(chunks).noneMatch(s->s.contains("Cedar Health") && (s.contains("Birch Payments")||s.contains("Terraform")));
        assertThat(chunks).anyMatch(s->s.startsWith("General skills")&&s.contains("Terraform"));
    }

    @Test void markdownHierarchySurvivesExtractionAndDoesNotCarrySiblingHeadings() {
        String markdown="# Atlas\n## Deployment\n"+"Use blue green releases. ".repeat(30)
                +"\n## Costs\nMonthly budget is 200 dollars.\n# Boreal\nUnrelated project.";
        String text=new MarkdownTextExtractor().extract(markdown.getBytes(StandardCharsets.UTF_8));
        var chunks=new SectionAwareChunker(300,40).chunk(text);
        assertThat(chunks.stream().filter(s->s.contains("releases"))).allMatch(s->s.startsWith("# Atlas\n## Deployment"));
        assertThat(chunks).anyMatch(s->s.startsWith("# Atlas\n## Costs")&&s.contains("200"));
        assertThat(chunks).noneMatch(s->s.contains("Boreal")&&s.contains("Atlas"));
    }

    @Test void unstructuredProseUsesTheExistingSplitter() {
        String text="A paragraph of ordinary prose with no section headings. ".repeat(20);
        assertThat(new SectionAwareChunker(300,40).chunk(text)).isEqualTo(new RecursiveChunker(300,40).chunk(text));
    }

    @Test void consecutiveHeadingsAndBlankInputRemainBounded() {
        var chunker=new SectionAwareChunker(100,10);
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("# A\n## B\n### C\n"+"word ".repeat(90))).allMatch(s->s.length()<=100);
    }
}
