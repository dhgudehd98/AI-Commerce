package com.sh.aicommerce.wms.inventory.service;

import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.common.exception.wms.InventoryException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.wms.StockMovementReferenceType;
import com.sh.aicommerce.enums.wms.StockMovementStatus;
import com.sh.aicommerce.wms.inBound.dto.InboundType;
import com.sh.aicommerce.wms.inventory.repository.ProductInventoryRepository;
import com.sh.aicommerce.wms.stockMovement.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductInventoryService {
    private final ProductInventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;

    @Transactional
    public void inBoundProduct(InboundType inboundType, Product product, ProductVariant variant, ProductOption option, Warehouse warehouse, int quantity, Integer safetyQuantity, Long inboundId) {

        // 최초 입고 / 재고추가 분기 처리
        try {
            if(inboundType == InboundType.INITIAL && safetyQuantity == null)
                throw new InventoryException("최초 입고시 안전재고에 대한 값은 필수입니다.");

            switch (inboundType) {
                case INITIAL -> initialInbound(product, variant, option, warehouse, quantity, safetyQuantity, inboundId);
                case ADDITIONAL -> additionalInbound(variant, option, option.getProduct().getId(), warehouse.getId(), quantity, inboundId);
            }
        } catch (Exception e) {
            log.error("[입고 과정중 오류 발생] 에러 메세지 : {}", e.getMessage());
            throw e;
        }
    }

    // 재고 최초 입고
    private void initialInbound(Product product, ProductVariant variant, ProductOption option, Warehouse warehouse, int quantity, Integer safetyQuantity, Long inboundId) {
        boolean exist = inventoryRepository.existsByProductOptionIdAndWarehouseId(option.getId(), warehouse.getId());

        // 최초 입고 상품인지 확인
        if(exist) throw new InventoryException("현재 입고가 되어 있는 상품입니다. 재고를 추가하시려면 재고추가를 이용해주세요.");

        // 최초 입고 등록
        ProductInventory inventory = ProductInventory.create(
                option, warehouse, quantity, safetyQuantity
        );

        option.onSale();
        variant.onSale();
        inventoryRepository.save(inventory);
        log.info("[최초 상품 입고 완료] 상품 옵션 ID : {}, 창고 ID : {}, 입고 수량 : {}", option.getId(), warehouse.getId(), quantity);

        // 재고 이동 이력 저장
        StockMovement stockMovement = new StockMovement(
                inventory,
                StockMovementStatus.INBOUND,
                StockMovementReferenceType.INITIAL_INBOUND,
                inboundId,
                quantity,
                0,
                quantity,
                LocalDateTime.now()
        );

        stockMovementRepository.save(stockMovement);

    }

    // 재고 추가 입고
    private void additionalInbound(ProductVariant variant, ProductOption option, Long productId, Long warehouseId, int quantity, Long inboundId) {

        // 상품 재고에 대한 정합성이 중요하기 때문에 비관적 락 설정
        ProductInventory inventory = inventoryRepository.findByProductOptionIdAndWarehouseIdForUpdate(option.getId(), warehouseId).orElseThrow(() -> new ProductException("현재 등록되어 있는 재고가 존재하지 않습니다."));

        // 입고 전 상품 수 취합 -> StockMovement에 설정하기 위해
        Integer beforeQuantity = inventory.getOnHandQuantity();
        inventory.increaseOnHandQuantity(quantity); // 재고 증가
        inventory.getProductOption().onSale();
        inventory.getProductOption().getProductVariant().onSale();
        Integer afterQuantity = inventory.getOnHandQuantity();

        StockMovement stockMovement = new StockMovement(
                inventory,
                StockMovementStatus.INBOUND,
                StockMovementReferenceType.ADDITIONAL_INBOUND,
                inboundId,
                quantity,
                beforeQuantity,
                afterQuantity, // afterQuantity
                LocalDateTime.now()
        );

        stockMovementRepository.save(stockMovement);
        log.info("[기존 상품 재고 추가] 상품 ID : {}, 상품 옵션 ID :{}", productId, option.getId());
    }
}
