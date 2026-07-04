package com.sh.aicommerce.product.es.repository;

import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.sh.aicommerce.common.exception.search.SearchException;
import com.sh.aicommerce.enums.product.ProductSearchSort;
import com.sh.aicommerce.product.es.document.ProductDocument;
import com.sh.aicommerce.search.dto.SearchResultProductDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class ProductDocumentNativeQueryImpl implements ProductDocumentNativeQuery{

    private final ElasticsearchOperations operations;
    @Override
    public List<SearchResultProductDto> search(String keyword, String sort, List<Object> searchAfter) {

        ProductSearchSort searchSort = ProductSearchSort.from(sort);

        NativeQueryBuilder builder = NativeQuery.builder()
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
                // 페이징 설정
                .withPageable(PageRequest.of(0, 10));
        applySort(builder, searchSort);

        NativeQuery query = builder.build();
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

    private void applySort(NativeQueryBuilder builder, ProductSearchSort searchSort) {
        switch (searchSort) {
            case PRICE_ASC -> {
                builder.withSort(s -> s.field(f ->
                        f.field("price").order(SortOrder.Asc)));
                builder.withSort(s -> s.field(f ->
                        f.field("productVariantId").order(SortOrder.Asc)));
            }
            case PRICE_DESC -> {
                builder.withSort(s -> s.field(f ->
                        f.field("price").order(SortOrder.Desc)));
                builder.withSort(s -> s.field(f ->
                        f.field("productVariantId").order(SortOrder.Asc)));
            }
            case LATEST -> builder.withSort(s ->
                    s.field(f -> f.field("productVariantId").order(SortOrder.Desc))
            );
            case RELEVANCE -> {
                builder.withSort(s -> s.field(f ->
                        f.field("_score").order(SortOrder.Desc)));
                builder.withSort(s -> s.field(f ->
                        f.field("productVariantId").order(SortOrder.Asc)));
            }
        }
    }

    @Override
    public List<SearchResultProductDto> findByVectors(float[] weatherVectors) {

        // 날씨 임베딩 정보가 정상적으로 넘어 왔는지 확인 여기까지 와서 안오면 에외처리
        if(weatherVectors == null || weatherVectors.length == 0) throw new SearchException("날씨와 관련된 정보가 존재하지 않습니다.");
        List<Float> vectors = new ArrayList<>();

        for (float f : weatherVectors) {
            vectors.add(f);
        }

        KnnQuery knnQuery = KnnQuery.of(k -> k
                .field("descriptionVector") // ES에 정의된 필드명
                .queryVector(vectors)
                .k(50)                       // 가져올 결과 개수
                .numCandidates(100)
        );

        NativeQuery nativeQuery = NativeQuery.builder()
                .withKnnQuery(knnQuery)
                .withPageable(PageRequest.of(0, 50))
                .build();

        SearchHits<ProductDocument> hits = operations.search(nativeQuery, ProductDocument.class);

        return hits.getSearchHits()
                .stream()
                .map(hit -> {
                    SearchResultProductDto dto = new SearchResultProductDto(hit.getContent());
                    dto.setScore(hit.getScore());
                    return dto;
                })
                .toList();

    }
}
