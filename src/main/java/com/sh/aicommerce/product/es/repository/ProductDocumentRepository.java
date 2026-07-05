package com.sh.aicommerce.product.es.repository;

import com.sh.aicommerce.product.es.document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductDocumentRepository extends
        ElasticsearchRepository<ProductDocument, Long>,
        ProductDocumentNativeQuery
{
    void deleteAllByProductId(Long productId);

}
