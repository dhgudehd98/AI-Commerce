package com.sh.aicommerce.product.service;

import com.sh.aicommerce.brand.repository.BrandRepository;
import com.sh.aicommerce.common.exception.brand.BrandException;
import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.entity.Brand;
import com.sh.aicommerce.entity.Product;
import com.sh.aicommerce.entity.ProductOption;
import com.sh.aicommerce.product.dto.ProductCreateRequestDto;
import com.sh.aicommerce.product.dto.ProductOptionCreateRequestDto;
import com.sh.aicommerce.product.dto.response.ProductCreateResponseDto;
import com.sh.aicommerce.product.repository.ProductRepository;
import com.sh.aicommerce.productOption.repository.ProductOptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final BrandRepository brandRepository;

    @Transactional
    public ProductCreateResponseDto createProduct(ProductCreateRequestDto createRequestDto) {

        // 상품 저장
        Brand brand = brandRepository.findById(createRequestDto.getBrandId()).orElseThrow(() -> new BrandException("해당 브랜드가 존재하지 않습니다. 브랜드를 다시 확인해주세요."));

        validateProductOption(createRequestDto.getProductOptions());
        log.info("[상품 옵션 유효성 검사 성공]");

        Product product = Product.create(createRequestDto, brand);
        productRepository.save(product);
        log.info("[상품 등록 완료] 상품 ID : {} , 상품명 : {}", product.getId(), product.getProductName());

        // 상품 옵션 저장
        List<ProductOption> options = createRequestDto.getProductOptions().stream()
                        .map(optionDto -> ProductOption.createOption(optionDto, product))
                        .collect(Collectors.toList());

        productOptionRepository.saveAll(options);
        log.info("[상품 옵션 저장 완료] 상품 옵션 개수 : {}", options.size());

        return new ProductCreateResponseDto("Y", "상품이 성공적으로 저장되었습니다.");
    }

    private void validateProductOption(List<ProductOptionCreateRequestDto> options) {

        log.info("[상품 옵션 유효성 검사 시작]");
        if (options == null || options.isEmpty()) throw new ProductException("상품 옵션을 한개 이상 등록해야 합니다.");

        Set<String> skus = new HashSet<>();
        Set<String> optionCombinations = new HashSet<>();

        for (ProductOptionCreateRequestDto optionDto : options) {
            String sku = optionDto.getSku();
            String combination = optionDto.getColor() + ":" + optionDto.getSize();

            if(!skus.add(sku)) throw new ProductException("요청에 중복된 sku가 존재합니다. sku : " + sku);
            if(!optionCombinations.add(combination))
                throw new ProductException("동일한 색상과 사이즈가 존재합니다. 옵션 : " + combination);

            // DB에 동일한 sku가 존재하는지 확인
            if(productOptionRepository.existsBySku(sku)) throw new ProductException("이미 등록되어 있는 SKU 입니다. SKU : " + sku);
        }
    }
}
