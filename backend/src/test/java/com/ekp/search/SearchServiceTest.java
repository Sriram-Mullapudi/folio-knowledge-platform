package com.ekp.search;
import com.ekp.embedding.*;
import com.ekp.embedding.ChunkVectorRepository.SearchHit;
import com.ekp.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchServiceTest {
    private final EmbeddingService embeddings = mock(EmbeddingService.class);
    private final ChunkVectorRepository vectors = mock(ChunkVectorRepository.class);
    private final KeywordRepository keywords = mock(KeywordRepository.class);
    private final SearchService service = new SearchService(embeddings, vectors, keywords);
    private final AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "test@example.com", "USER");
    private SearchHit hit() { return new SearchHit(UUID.randomUUID(), UUID.randomUUID(), 0, "text", 0, .9); }

    @Test void keywordSearchWorksWithoutEmbeddingsAndScopesOwnerAndDocument() {
        UUID document = UUID.randomUUID(); SearchHit hit = hit();
        when(keywords.search("error", user.id(), document, 15)).thenReturn(List.of(hit));
        assertThat(service.search(new SearchRequest("error", document, null), user))
            .extracting(SearchHitResponse::chunkId).containsExactly(hit.chunkId());
        verifyNoInteractions(vectors); verify(embeddings, never()).embedQuery(anyString());
    }
    @Test void combinesBothRankingsAndPrioritizesAgreement() {
        SearchHit a = hit(), b = hit(), common = hit(); float[] query = {1};
        when(embeddings.isUsable()).thenReturn(true);
        when(embeddings.embedQuery("policy")).thenReturn(query);
        when(keywords.search("policy", user.id(), null, 6)).thenReturn(List.of(a, common));
        when(vectors.search(query, user.id(), null, 6)).thenReturn(List.of(b, common));
        var result = service.search(new SearchRequest("policy", null, 2), user);
        assertThat(result).hasSize(2); assertThat(result.get(0).chunkId()).isEqualTo(common.chunkId());
    }
    @Test void boundsCandidateCount() {
        when(keywords.search("q", user.id(), null, 100)).thenReturn(List.of());
        assertThat(service.search(new SearchRequest("q", null, 9999), user)).isEmpty();
        verify(keywords).search("q", user.id(), null, 100);
    }
    @Test void fusionIsStableAndDoesNotCountDuplicatesWithinOneRanking() {
        SearchHit a = hit(), b = hit();
        var result = RankFusion.fuse(List.of(List.of(a, a), List.of(b)), 10);
        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(h -> assertThat(h.similarity()).isEqualTo(1.0 / 61));
        assertThat(result).isEqualTo(RankFusion.fuse(List.of(List.of(b), List.of(a, a)), 10));
    }
}
