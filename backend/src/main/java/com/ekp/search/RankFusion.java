package com.ekp.search;

import com.ekp.embedding.ChunkVectorRepository.SearchHit;
import java.util.*;

/** Reciprocal rank fusion scores are ranking weights, not probabilities. */
public final class RankFusion {
    private RankFusion() {}
    public static List<SearchHit> fuse(List<List<SearchHit>> rankings, int limit) {
        Map<UUID, SearchHit> hits = new HashMap<>();
        Map<UUID, Double> scores = new HashMap<>();
        for (List<SearchHit> ranking : rankings) {
            Set<UUID> seen = new HashSet<>();
            for (int i = 0; i < ranking.size(); i++) {
                SearchHit hit = ranking.get(i);
                if (!seen.add(hit.chunkId())) continue;
                hits.putIfAbsent(hit.chunkId(), hit);
                scores.merge(hit.chunkId(), 1.0 / (60 + i + 1), Double::sum);
            }
        }
        return hits.values().stream()
            .sorted(Comparator.<SearchHit>comparingDouble(h -> scores.get(h.chunkId())).reversed()
                .thenComparing(h -> h.chunkId().toString()))
            .limit(limit)
            .map(h -> new SearchHit(h.chunkId(), h.documentId(), h.chunkIndex(), h.content(),
                0, scores.get(h.chunkId())))
            .toList();
    }
}
