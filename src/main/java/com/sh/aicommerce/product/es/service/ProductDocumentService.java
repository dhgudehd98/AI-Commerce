
package com.sh.aicommerce.product.es.service;


import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.entity.Product;
import com.sh.aicommerce.product.es.document.ProductDocument;
import com.sh.aicommerce.product.es.record.ProductIndexRecord;
import com.sh.aicommerce.product.es.repository.ProductDocumentRepository;
import com.sh.aicommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductDocumentService {
    private final ProductDocumentRepository productDocumentRepository;
    private final EmbeddingModel embeddingModel;
    private final ProductIndexService indexService;


    // 상품 등록
    public List<ProductDocument> insertProductVariantDocument(Long productId) {
        ProductIndexRecord productRecord = indexService.productEntityToRecord(productId);

        // 상품 설명 임베딩 처리
        float[] descriptionVectors = embeddingModel.embed(productRecord.productDescription());

        return productRecord.variantRecords().stream()
                .map(variantRecord -> ProductDocument.createProduct(productRecord, variantRecord, descriptionVectors))
                .toList();
    }

    // 상품 입고 후
    public List<ProductDocument> inboundProductVariantDocument(Long productId) {

        ProductIndexRecord productRecord = indexService.productEntityToRecord(productId);
        float[] descriptionVector = embeddingModel.embed(productRecord.productDescription());

        return productRecord.variantRecords().stream()
                .map(variantRecord -> ProductDocument.inboundProductVariantDocumentFromRecord(productRecord, variantRecord, descriptionVector))
                .toList();
    }

    // ES에 저장된 Product 상품 삭제
    //! 나중에 Variant에 대한 부분 삭제 추가
    public void deleteProductDocument(Long productId) {
        productDocumentRepository.deleteAllByProductId(productId);
    }

}