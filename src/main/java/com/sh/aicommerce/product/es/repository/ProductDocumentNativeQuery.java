package com.sh.aicommerce.product.es.repository;

import com.sh.aicommerce.search.dto.SearchResultProductDto;

import java.util.List;

public interface ProductDocumentNativeQuery {
    List<SearchResultProductDto> search(String keyword , List<Object> searchAfter);
}
