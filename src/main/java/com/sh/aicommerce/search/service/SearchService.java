package com.sh.aicommerce.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.sh.aicommerce.brand.es.BrandDocument;
import com.sh.aicommerce.product.es.document.ProductDocument;
import com.sh.aicommerce.product.es.repository.ProductDocumentRepository;
import com.sh.aicommerce.search.dto.SearchResultProductDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {
    private final ElasticsearchClient elasticsearchClient;
    private final ProductDocumentRepository productDocumentRepository;


    // 검색 -> 모든 상품 조회
    public List<SearchResultProductDto> search(String keyword, List<Object> searchAfter) {

        // 인기 검색어 저장
        List<SearchResultProductDto> results = productDocumentRepository.search(keyword, searchAfter);
        log.info("[상품 검색] 검색어 : {} , 검색 결과 : {}", keyword, results.size());
        return results;
    }

    /**
     * elasticSearchClient : ElasticSearch 서버에 검색을 요청을 보내고 결과 값을 받아옴
     * suggest : 자동 요청 설정 구성
     * suggests : 자동 요청 설정에 구성에 필요한 구성품
     */
    public List<BrandDocument> productAutoCompletion(String prefix) throws IOException {
        SearchResponse<BrandDocument> response = elasticsearchClient.search(searchRequest -> searchRequest
                        .index("brands") // "brands" Index에서 검색
                        .suggest(suggestBuilder -> suggestBuilder // suggest : 자동 완성 구성 요청
                                // brand_suggest에 대한 값은 ES 안에 필드 값이 아닌 자동완성 요청 결과의 이름
                                // KEY : "brand_suggest" -> Value : 자동 완성 결과 return 값
                                .suggesters("brand_suggest", su -> su
                                        .prefix(prefix)
                                        .completion(c -> c
                                                .field("suggest")
                                                .size(5))
                                )
                        ),
                BrandDocument.class
        );

        return response.suggest()
                .get("brand_suggest")
                .stream()
                .flatMap(s -> s.completion().options().stream())
                .map(option -> option.source())
                .collect(Collectors.toList());
    }

}