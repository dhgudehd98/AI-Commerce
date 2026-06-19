
package com.sh.aicommerce.product.es.service;


import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.entity.Product;
import com.sh.aicommerce.entity.ProductVariant;
import com.sh.aicommerce.product.es.document.ProductDocument;
import com.sh.aicommerce.product.es.repository.ProductDocumentRepository;
import com.sh.aicommerce.product.repository.ProductRepository;
import com.sh.aicommerce.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductDocumentService {
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductDocumentRepository productDocumentRepository;
    private final EmbeddingModel embeddingModel;

    @Transactional(readOnly = true)
    public List<ProductDocument> upSertDocument(Long productId) {

        Product product = productRepository.findWithBrandAndVariantsByProductId(productId).orElseThrow(() -> new ProductException("해당 상품이 존재하지 않습니다."));

        //! 상품 설명 임베딩 처리 - OpenAI API 플랫폼 결제 후 해당 기능 주석해제
//        float[] descriptionVectors = embeddingModel.embed(product.getProductDescription());
//        return new ProductDocument(product, descriptionVectors);


        return product.getVariants().stream()
                .map(productVariant -> ProductDocument.create(product, productVariant))
                .toList();
    }

    // ES에 저장된 Product 상품 삭제
    //! 나중에 Variant에 대한 부분 삭제 추가
    public void deleteProductDocument(Long productId) {
        productDocumentRepository.deleteAllByProductId(productId);
    }

}