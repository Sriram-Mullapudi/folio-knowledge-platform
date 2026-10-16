package com.ekp.search;

import com.ekp.embedding.ChunkVectorRepository;
import com.ekp.embedding.EmbeddingService;
import com.ekp.security.AuthenticatedUser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Fuses keyword and vector rankings, with keyword-only operation when embeddings
 * are disabled. All queries are scoped to the caller's
 * documents. Results never cross tenant boundaries: ownership is enforced in the
 * SQL, not just here.
 */
@Service
public class SearchService {

    private static final int DEFAULT_K = 5;
    private static final int MAX_K = 50;

    private final EmbeddingService embeddings;
    private final ChunkVectorRepository vectors;
    private final KeywordRepository keywords;

    public SearchService(EmbeddingService embeddings, ChunkVectorRepository vectors, KeywordRepository keywords) {
        this.embeddings = embeddings;
        this.vectors = vectors;
        this.keywords = keywords;
    }

    public List<SearchHitResponse> search(SearchRequest request, AuthenticatedUser user) {
        return retrieve(request, user).stream().map(SearchHitResponse::from).toList();
    }

    public List<ChunkVectorRepository.SearchHit> retrieve(SearchRequest request, AuthenticatedUser user) {
        int k = clampK(request.k());
        int candidates = Math.min(100, k * 3);
        var lexical = request.collectionId()==null ? keywords.search(request.query(), user.id(), request.documentId(), candidates) : keywords.search(request.query(),user.id(),request.documentId(),candidates,request.collectionId());
        if (!embeddings.isUsable()) return RankFusion.fuse(List.of(lexical), k);
        float[] queryVector = embeddings.embedQuery(request.query());
        List<ChunkVectorRepository.SearchHit> hits =
                request.collectionId()==null ? vectors.search(queryVector, user.id(), request.documentId(), candidates) : vectors.search(queryVector,user.id(),request.documentId(),candidates,request.collectionId());
        return RankFusion.fuse(List.of(hits, lexical), k);
    }

    private int clampK(Integer requested) {
        if (requested == null) {
            return DEFAULT_K;
        }
        return Math.min(Math.max(requested, 1), MAX_K);
    }
}
