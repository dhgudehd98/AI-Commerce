package com.sh.aicommerce.search.controller;

import com.sh.aicommerce.brand.es.BrandDocument;
import com.sh.aicommerce.product.es.document.ProductDocument;
import com.sh.aicommerce.search.dto.SearchResultProductDto;
import com.sh.aicommerce.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;
    // 상품 검색 결과 출력
    @GetMapping("")
    public List<SearchResultProductDto> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long lastId,
            @RequestParam(required = false) Double lastScore
    ) {
        List<Object> searchAfter = null;

        if (lastId != null && lastScore != null) searchAfter = List.of(lastScore, lastId);

        return searchService.search(keyword, searchAfter);
    }
    // 상품 자동 완성
    @GetMapping("autoCompletion")
    public List<BrandDocument> productAutoCompletion(@RequestParam(required = false) String prefix) throws IOException {
        return searchService.productAutoCompletion(prefix);

    }

    // 상품 검색

}