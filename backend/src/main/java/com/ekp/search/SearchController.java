package com.ekp.search;

import com.ekp.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "Hybrid keyword and semantic search over your documents")
public class SearchController {

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Top-k hybrid search across the current user's chunks")
    public List<SearchHitResponse> search(@Valid @RequestBody SearchRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        return service.search(request, user);
    }
}
