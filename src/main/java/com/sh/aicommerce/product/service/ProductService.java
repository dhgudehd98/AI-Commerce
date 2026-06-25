package com.sh.aicommerce.product.service;


import com.sh.aicommerce.brand.repository.BrandRepository;
import com.sh.aicommerce.common.exception.brand.BrandException;
import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.product.ProductImageType;
import com.sh.aicommerce.product.dto.request.ProductCreateRequestDto;
import com.sh.aicommerce.product.dto.request.ProductImageCreateRequestDto;
import com.sh.aicommerce.product.dto.request.ProductOptionCreateRequestDto;
import com.sh.aicommerce.product.dto.request.ProductVariantRequestDto;
import com.sh.aicommerce.product.dto.response.ProductCreateResponseDto;
import com.sh.aicommerce.product.redis.ProductIndexEventRecord;
import com.sh.aicommerce.product.repository.ProductImageRepository;
import com.sh.aicommerce.product.repository.ProductRepository;
import com.sh.aicommerce.product.repository.ProductVariantRepository;
import com.sh.aicommerce.productOption.repository.ProductOptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j

public class ProductService {
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final BrandRepository brandRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;

    private final ApplicationEventPublisher eventPublisher;


    @Transactional
    public ProductCreateResponseDto createProduct(List<ProductCreateRequestDto> dtos) {
        for (ProductCreateRequestDto createRequestDto : dtos) {
            Brand brand = brandRepository.findById(createRequestDto.getBrandId()).orElseThrow(() -> new BrandException("현재 존재하지 않는 브랜드입니다. 브랜드를 다시 확인해주세요."));

            // 요청 값 유효성검사 -> 상품, 상품 Variant, 상품 옵션, 상품 이미지
            validateProductRequest(createRequestDto.getVariants());

            // 유효성 검사 통과하면 상품에 대한 데이터 저장
            // 1. 상품 데이터 저장
            Product product = Product.create(createRequestDto, brand);
            productRepository.save(product);
            // 2. 상품 Variandant 저장
            try {
                saveVariant(product, createRequestDto.getVariants());
            } catch (DataIntegrityViolationException e) {
                throw new ProductException("이미 등록된 모델번호 또는 SKU에 대한 값이 존재합니다.");
            }

            // 상품에 대한 정보를 색인하는 것이 아닌 상품 Variant 대한 부분을 색인
            eventPublisher.publishEvent(
                    new ProductIndexEventRecord(product.getId(), "CREATE")
            );
        }
        return new ProductCreateResponseDto("Y", "상품이 성공적으로 저장되었습니다.");
    }

    private void saveVariant(Product product, List<ProductVariantRequestDto> variants) {

        for (ProductVariantRequestDto variantRequestDto : variants) {

            //상품 Variant 저장
            ProductVariant variant = ProductVariant.create(product, variantRequestDto);
            productVariantRepository.save(variant);

            saveProductImages(variant, variantRequestDto.getImages());
            saveProductOptions(variant, variantRequestDto.getProductOptions());
        }
    }

    private void saveProductImages(ProductVariant variant, List<ProductImageCreateRequestDto> dtoImages) {
        List<ProductImage> images = dtoImages.stream()
                .map(imageDto -> ProductImage.create(variant, imageDto))
                .toList();

        productImageRepository.saveAll(images);
    }
    private void saveProductOptions(ProductVariant variant, List<ProductOptionCreateRequestDto> optionDtos) {
        List<ProductOption> options = optionDtos.stream()
                .map(optionDto -> ProductOption.createOption(variant, optionDto))
                .toList();

        productOptionRepository.saveAll(options);
    }


    private void validateProductRequest(List<ProductVariantRequestDto> variants) {

        if(variants == null || variants.isEmpty()) throw new ProductException("상품 Variant에 대한 값을 하나 이상 등록해야합니다.");

        Set<String> skus = new HashSet<>();
        Set<String> models = new HashSet<>();
        Set<String> colors = new HashSet<>();

        for (ProductVariantRequestDto variantRequestDto : variants) {
            validateVariantRequestDto(variantRequestDto, skus, models, colors);
        }


    }

    private void validateVariantRequestDto(ProductVariantRequestDto variantRequestDto, Set<String> skus, Set<String> models, Set<String> colors) {

        if(!models.add(variantRequestDto.getModelNumber()))
            throw new ProductException("중복된 모델번호가 존재합니다. 모델번호 : " + variantRequestDto.getModelNumber());

        if (!colors.add(variantRequestDto.getColor())) {
            throw new ProductException("중복된 색상이 존재합니다. 색상 : " + variantRequestDto.getColor());
        }

        // 이미지 유효성 검사
        validateProductImageRequestDto(variantRequestDto);

        // 상품 옵션 유효성 검사
        validateProductOptionRequestDto(variantRequestDto.getProductOptions(), skus);

    }

    private void validateProductImageRequestDto(ProductVariantRequestDto variantRequestDto) {

        // 이미지 순서
        Set<Integer> orders = new HashSet<>();

        // 썸네일 이미지는 하나만 등록하는 유효성 검사
        long thumbNailImageCount = variantRequestDto.getImages().stream()
                .filter(productImageCreateRequestDto -> productImageCreateRequestDto.getImageType() == ProductImageType.THUMBNAIL)
                .count();

        if(thumbNailImageCount != 1 ) throw new ProductException("썸네일 이미지는 하나만 등록할 수 있습니다.");

        for (ProductImageCreateRequestDto imageDto : variantRequestDto.getImages()) {
            if(!orders.add(imageDto.getDisplayOrder()))
                throw new ProductException(variantRequestDto.getVariantName() + " 에 중복된 이미지 표시 순서가 존재합니다.");
        }
    }

    // 상품 옵션 유효성 검사
    private void validateProductOptionRequestDto(List<ProductOptionCreateRequestDto> productOptions, Set<String> skus) {
        Set<String> sizes = new HashSet<>();
        for (ProductOptionCreateRequestDto optionDto : productOptions) {
            if(!skus.add(optionDto.getSku()))
                throw new ProductException("동일한 SKU에 대한 값이 존재합니다. SKU 값 : " + optionDto.getSku());

            if(!sizes.add(optionDto.getSize()))
                throw new ProductException("중복된 사이즈 값이 있습니다. SIZE : " + optionDto.getSize());

            if(productOptionRepository.existsBySku(optionDto.getSku()))
                throw new ProductException("이미 등록된 SKU 입니다. SKU : " + optionDto.getSku());
        }
    }


    //! 여기 상품 삭제에 대한 부분 수정해야됨
    @Transactional
    public ProductCreateResponseDto deleteProduct(Long id) {

        try {
            // 상품이랑 , 상품 옵션에 대한 정보가 존재하는지 확인
//            if (productRepository.existsById(id) && productOptionRepository.existsByProductId(id)) {
//                productOptionRepository.deleteByProductId(id);
//                productRepository.deleteById(id);
//            }

            eventPublisher.publishEvent(
                    new ProductIndexEventRecord(id, "DELETE")
            );
        } catch (Exception e) {
            log.error("[상품 삭제 에러 발생] 상품 번호 : {} , 에러메세지 : {}", id, e.getMessage());
        }

        eventPublisher.publishEvent(
                new ProductIndexEventRecord(id, "DELETE")
        );

        return new ProductCreateResponseDto("Y", "상품이 성공적으로 삭제되었습니다.");
    }
}