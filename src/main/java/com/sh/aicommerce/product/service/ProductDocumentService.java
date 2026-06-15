package com.sh.aicommerce.product.service;


import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.entity.Product;
import com.sh.aicommerce.product.es.ProductDocument;
import com.sh.aicommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductDocumentService {
    private final ProductRepository productRepository;
    private final EmbeddingModel embeddingModel;

    @Transactional(readOnly = true)
    public ProductDocument upSertDocument(Long productId) {
        Product product = productRepository.findWithBrandByproductId(productId).orElseThrow(() -> new ProductException("해당 상품이 존재하지 않습니다."));

        //! 상품 설명 임베딩 처리 - OpenAI API 플랫폼 결제 후 해당 기능 주석해제
//        float[] descriptionVectors = embeddingModel.embed(product.getProductDescription());
//        return new ProductDocument(product, descriptionVectors);

        return new ProductDocument(product);
    }

}