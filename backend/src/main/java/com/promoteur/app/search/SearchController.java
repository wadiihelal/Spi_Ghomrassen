package com.promoteur.app.search;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Recherche", description = "Recherche globale : clients, lots, contrats, fournisseurs, factures.")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "Recherche globale, au plus cinq résultats par type")
    @GetMapping
    public List<SearchHitResponse> search(@RequestParam("q") String query,
                                          @RequestParam(required = false) Long projectId) {
        return searchService.search(query, projectId);
    }
}
