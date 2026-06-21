package com.sh.aicommerce.wms.inBound.service;

import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.common.exception.wms.WarehouseException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.product.repository.ProductRepository;
import com.sh.aicommerce.product.repository.ProductVariantRepository;
import com.sh.aicommerce.productOption.repository.ProductOptionRepository;
import com.sh.aicommerce.wms.inBound.dto.InboundRequestDto;
import com.sh.aicommerce.wms.inBound.dto.ProductInboundResponseRecord;
import com.sh.aicommerce.wms.inBound.dto.ProductOptionInboundReqDto;
import com.sh.aicommerce.wms.inBound.dto.ProductVariantInboundReqDto;
import com.sh.aicommerce.wms.inBound.repository.InboundItemRepository;
import com.sh.aicommerce.wms.inBound.repository.InboundRepository;
import com.sh.aicommerce.wms.inventory.service.ProductInventoryService;
import com.sh.aicommerce.wms.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class InboundService {

    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductInventoryService productInventoryService;
    private final WarehouseRepository warehouseRepository;
    private final InboundRepository inboundRepository;
    private final InboundItemRepository inboundItemRepository;
    private final ProductVariantRepository variantRepository;

    @Transactional
    public ProductInboundResponseRecord inBoundProduct(InboundRequestDto inboundRequestDto) {
        /**
         * 상품 입/출고시
         * - Inbound : 입고 내역 저장
         * - InboundItem : 입고 아이템 상세 저장
         * - StockMovement 에 입/출고 내역 저장
         * - ProductInventory(InventoryService) : 실제 입출고 재고 정산
         */

        Product product = productRepository.findById(inboundRequestDto.getProductId()).orElseThrow(() -> new ProductException("해당 ID에 존재하는 상품이 없습니다. 상품 ID : " + inboundRequestDto.getProductId()));
        Warehouse warehouse = warehouseRepository.findById(inboundRequestDto.getWarehouseId()).orElseThrow(() -> new WarehouseException("해당 창고가 존재하지 않습니다."));

        //입고 내역 저장
        Inbound inbound = new Inbound(
                generateInboundNumber(),  // 입고 번호
                inboundRequestDto.getInboundType(), // 입고 타입
                product,
                warehouse,
                LocalDateTime.now()
        );
        inboundRepository.save(inbound);

        log.info("[상품 입고 신청] 상품 정보 - 상품 아이디 : {}", inboundRequestDto.getProductId());

        for (ProductVariantInboundReqDto variantDto : inboundRequestDto.getItems()) {
            ProductVariant variant = variantRepository.findByIdAndProductId(variantDto.getProductVariantId(), product.getId()).orElseThrow(() -> new ProductException("해당 정보와 일치하지는 Variant가 존재하지 않습니다."));

            for (ProductOptionInboundReqDto optionDto : variantDto.getOptions()) {
                log.info("[상품 입고 신청 상세 정보] 상품 Variant ID : {}, 상품 옵션 ID : {}", variantDto.getProductVariantId(), optionDto.getProductOptionId());
                ProductOption option = productOptionRepository.findByProductIdAndNoHiddenProductOption(optionDto.getProductOptionId(), product.getId(), variantDto.getProductVariantId()).orElseThrow(() -> new ProductException("해당 정보와 일치하지 않는 상품이 존재하지 않습니다."));
                productInventoryService.inBoundProduct(inboundRequestDto.getInboundType(), product, variant, option, warehouse, optionDto.getInboundCount(), optionDto.getSafetyQuantity(), inbound.getId());

                InboundItem inboundItem = new InboundItem(
                        inbound,
                        option,
                        optionDto.getInboundCount(),
                        LocalDateTime.now()
                );

                inboundItemRepository.save(inboundItem);
            }
        }

        return new ProductInboundResponseRecord(
                product.getId(),
                warehouse.getId(),
                "Y",
                "상품이 성공적으로 입고 완료되었습니다."
        );
    }

    public String generateInboundNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();

        return "INB-" + date + "-" + suffix;
    }
}