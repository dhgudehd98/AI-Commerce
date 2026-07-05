package com.sh.aicommerce.product.es.service;

import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.entity.Product;
import com.sh.aicommerce.product.es.record.ProductIndexRecord;
import com.sh.aicommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductIndexService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public ProductIndexRecord productEntityToRecord(Long productId) {
        Product product = productRepository.findWithBrandAndVariantsByProductId(productId).orElseThrow(() -> new ProductException("해당 상품이 존재하지 않습니다."));

        return ProductIndexRecord.from(product);
    }
}