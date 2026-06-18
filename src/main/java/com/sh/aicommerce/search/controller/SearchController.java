package com.sh.aicommerce.search.controller;

import com.sh.aicommerce.brand.es.BrandDocument;
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
    @GetMapping("result")
    public void productSearchResult() {

    }
    // 상품 자동 완성
    @GetMapping("autoCompletion")
    public List<BrandDocument> productAutoCompletion(@RequestParam(required = false) String prefix) throws IOException {
        return searchService.productAutoCompletion(prefix);

    }

    // 상품 검색

}