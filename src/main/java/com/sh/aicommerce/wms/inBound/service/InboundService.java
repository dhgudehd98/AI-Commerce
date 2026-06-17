package com.sh.aicommerce.wms.inBound.service;

import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.common.exception.wms.WarehouseException;
import com.sh.aicommerce.entity.Product;
import com.sh.aicommerce.entity.ProductOption;
import com.sh.aicommerce.entity.Warehouse;
import com.sh.aicommerce.product.repository.ProductRepository;
import com.sh.aicommerce.productOption.repository.ProductOptionRepository;
import com.sh.aicommerce.wms.inBound.dto.InboundRequestDto;
import com.sh.aicommerce.wms.inBound.dto.ProductInboundResponseRecord;
import com.sh.aicommerce.wms.inBound.dto.ProductOptionInboundReqDto;
import com.sh.aicommerce.wms.inventory.service.ProductInventoryService;
import com.sh.aicommerce.wms.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Slf4j
public class InboundService {

    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductInventoryService productInventoryService;
    private final WarehouseRepository warehouseRepository;

    @Transactional
    public ProductInboundResponseRecord inBoundProduct(InboundRequestDto inboundRequestDto) {
        /**
         * 상품 입/출고시
         * - StockMovement 에 입/출고 내역 저장
         * - ProductInventory(InventoryService) : 실제 입출고 재고 정산
         */
        // 상품 조회 할 떄 ProductStatus에 대한 값이 "STOPPED"가 아니면 조회
        Product product = productRepository.findNoStoppedProduct(inboundRequestDto.getProductId()).orElseThrow(() -> new ProductException("해당 상품에 대한 정보가 존재하지 않습니다."));
        Warehouse warehouse = warehouseRepository.findById(inboundRequestDto.getWarehouseId()).orElseThrow(() -> new WarehouseException("해당 창고가 존재하지 않습니다."));

        for (ProductOptionInboundReqDto dto : inboundRequestDto.getItems()) {

            // 실제 상품에 대한 재고 수량 증가
            log.info("[상품 입고] 상품정보 - 상품 아이디 : {}, 상품 옵션 아이디 : {}, 상품 수량 : {}", inboundRequestDto.getProductId(), dto.getProductOptionId(), dto.getInboundCount());

            // ProductOption에 대한 값도 status에 대한 값이 'HIDDEN'에 대한 값이 아닌걸로만 설정
            ProductOption productOption = productOptionRepository.findByProductIdAndNoHiddenProductOption(product.getId(), dto.getProductOptionId()).orElseThrow(() -> new ProductException("해당 옵션에 대한 정보가 없습니다."));
            productInventoryService.inBoundProduct(product, productOption, warehouse, dto.getInboundCount(),dto.getSafetyQuantity());
        }
        return new ProductInboundResponseRecord(
                product.getId(),
                warehouse.getId(),
                "Y",
                "상품이 성공적으로 입고 완료되었습니다."
        );
    }
}