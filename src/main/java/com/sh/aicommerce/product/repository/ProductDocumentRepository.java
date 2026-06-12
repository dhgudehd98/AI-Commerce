package com.sh.aicommerce.product.repository;

import com.sh.aicommerce.product.es.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductDocumentRepository extends ElasticsearchRepository<ProductDocument, Long> {

}
