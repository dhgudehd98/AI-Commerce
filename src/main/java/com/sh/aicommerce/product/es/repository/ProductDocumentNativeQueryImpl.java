package com.sh.aicommerce.product.es.repository;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.sh.aicommerce.product.es.document.ProductDocument;
import com.sh.aicommerce.search.dto.SearchResultProductDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.List;

@RequiredArgsConstructor
public class ProductDocumentNativeQueryImpl implements ProductDocumentNativeQuery{

    private final ElasticsearchOperations operations;
    @Override
    public List<SearchResultProductDto> search(String keyword , List<Object> searchAfter) {
        NativeQuery query = NativeQuery.builder()
                // Query 설정
                .withQuery(q -> q
                        .bool(b -> b
                                .must(m -> m
                                        .multiMatch(mm -> mm
                                                .query(keyword)
                                                .fields(
                                                        "brandName^100",
                                                        "baseProductName^30",
                                                        "variantName^50",
                                                        "productDescription^5",
                                                        "tags^5"
                                                )
                                                .type(TextQueryType.CrossFields)
                                                .minimumShouldMatch("70%")
                                        )
                                )
                                // should : must에 해당하는 상품들중 추가 점수 주고 싶을 때
                                // variantName에 대한 값과 검색어에 대한 값이 일치하면 추가점수
                                .should(s -> s
                                        .term(t -> t
                                                .field("variantName.keyword")
                                                .value(keyword)
                                                .boost(250f)
                                        )
                                )
                                .should(s -> s
                                        .term(t -> t
                                                .field("brandName.keyword")
                                                .value(keyword)
                                                .boost(250f)
                                        )
                                )
                                //! 나중에 여기는 주석해제
//                                .filter(f -> f
//                                        .term(t -> t
//                                                .field("inStock")
//                                                .value(true)
//                                        )
//                                )
                        )
                )
                // 2. 정렬 설정 - 스코어에대한 값 기준으로 내림차순 , 스코어에 대한 부분이 동일하다면 id에 대한 값은 오름차순으로 설정
                .withSort(s -> s
                        .field(f -> f.field("_score").order(SortOrder.Desc))
                )
                .withSort(s -> s
                        .field(f -> f.field("productVariantId").order(SortOrder.Asc))
                )
                // 페이징 설정
                .withPageable(PageRequest.of(0, 10))
                .build();

        if (searchAfter != null && !searchAfter.isEmpty()) {
            query.setSearchAfter(searchAfter);
        }

        // ES index 검색 쿼리 설정
        SearchHits<ProductDocument> searchHits = operations.search(query, ProductDocument.class);

        return searchHits.getSearchHits()
                .stream()
                .map(hit -> {
                    SearchResultProductDto dto = new SearchResultProductDto(hit.getContent());
                    dto.setScore(hit.getScore());
                    return dto;
                })
                .toList();
    }
}
