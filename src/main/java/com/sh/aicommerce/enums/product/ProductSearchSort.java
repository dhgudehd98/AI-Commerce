package com.sh.aicommerce.enums.product;

import java.util.List;

public enum ProductSearchSort {
    PRICE_DESC,
    PRICE_ASC,
    LATEST,
    RELEVANCE;

    public static ProductSearchSort from(String sort) {

        if (sort == null || sort.isBlank()) {
            return ProductSearchSort.RELEVANCE;
        }

        try {
            return ProductSearchSort.valueOf(sort.toUpperCase());
        } catch (IllegalArgumentException e) {
            return RELEVANCE;
        }
    }

    public static List<Object> createSearchAfter(
            String sort,
            Long lastId,
            Double lastScore,
            Integer lastPrice
    ) {
        ProductSearchSort searchSort = ProductSearchSort.from(sort);

        // 정렬 없이 검색 했을 때는 검색어 기준으로 출력
        if (searchSort == RELEVANCE) {
            if(lastId == null || lastScore == null) return null;

            return List.of(lastScore, lastId);
        }

        // 가격에 대한 기준으로 정렬 했을 때는 가격 + 상품 ID에 대한 값으로 출력
        if (searchSort == PRICE_DESC || searchSort == PRICE_ASC) {
            if(lastId == null || lastPrice == null) return null;

            return List.of(lastPrice, lastId);
        }

        // 최신순에 대해서 정렬을 했을 때는 상품 ID에 대한 값으로 출력
        if (searchSort == LATEST) {
            if(lastId == null) return null;
            return List.of(lastId);
        }

        return null;
    }
}
