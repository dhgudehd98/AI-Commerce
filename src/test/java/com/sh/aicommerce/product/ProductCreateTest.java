package com.sh.aicommerce.product;

import com.sh.aicommerce.brand.repository.BrandRepository;
import com.sh.aicommerce.entity.Brand;
import com.sh.aicommerce.enums.product.ProductCategory;

import com.sh.aicommerce.product.service.ProductService;
import com.sh.aicommerce.productOption.repository.ProductOptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class ProductCreateTest {

    @Autowired
    ProductService productService;

    @Autowired
    BrandRepository brandRepository;

    @Autowired
    ProductOptionRepository productOptionRepository;

    @Test
    void 동일한_SKU로_동시에_상품_등록시_하나만_성공() throws Exception {
        Brand brand = brandRepository.findById(1L)
                .orElseThrow();

        int threadCount = 2;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        String duplicatedSku = "TEST-DUP-SKU-001";

        for (int i = 0; i < threadCount; i++) {
            int index = i;

//            executorService.submit(() -> {
//                try {
//                    ProductCreateRequestDto request = new ProductCreateRequestDto(
//                            brand.getId(),
//                            "동시성 테스트 상품 " + index,
//                            ProductCategory.OUTER,
//                            150000,
//                            "동일 SKU 동시 등록 테스트",
//                            List.of(
//                                    new ProductOptionCreateRequestDto(
//                                            duplicatedSku,
//                                            "BLACK",
//                                            "L",
//                                            0
//                                    )
//                            )
//                    );
//
//                    readyLatch.countDown();
//                    startLatch.await();
//
//                    productService.createProduct(request);
//                    successCount.incrementAndGet();
//
//                } catch (Exception e) {
//                    failCount.incrementAndGet();
//
//                } finally {
//                    doneLatch.countDown();
//                }
//            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        executorService.shutdown();
        int count = productOptionRepository.countBySku(duplicatedSku);

        System.out.println("SKU COUNT : " + count);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
    }
}