package com.sh.aicommerce.wms.inventory.service;

import com.sh.aicommerce.common.exception.wms.InventoryException;
import com.sh.aicommerce.entity.Product;
import com.sh.aicommerce.entity.ProductInventory;
import com.sh.aicommerce.entity.ProductOption;
import com.sh.aicommerce.entity.Warehouse;
import com.sh.aicommerce.wms.inBound.dto.ProductOptionInboundReqDto;
import com.sh.aicommerce.wms.inventory.repository.ProductInventoryRepository;
import com.sh.aicommerce.wms.stockMovement.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductInventoryService {
    private final ProductInventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;

    @Transactional
    public void inBoundProduct(Product product, ProductOption option, Warehouse warehouse, int quantity, Integer safetyQuantity) {

        try {
            ProductInventory inventory =
                    inventoryRepository.findByProductOptionIdAndWarehouseId(option.getId(), warehouse.getId())
                            .map(existingInventory -> {
                                // 기존에 있던 상품 수량 증가
                                log.info("[기존 상품 재고 추가] 상품 ID : {}, 상품 옵션 ID :{}", option.getProduct().getId(), option.getId());
                                existingInventory.increaseOnHandQuantity(quantity);
                                return existingInventory;
                            })
                            .orElseGet(() -> {
                                // 최초 상품 입고
                                log.info("[최초 입고] 상품 ID : {} , 상품 옵션 ID : {}", option.getProduct().getId(), option.getId());
                                if(safetyQuantity == null || safetyQuantity <= 0) throw new InventoryException("최초 입고 시 안전 재고 수량은 필수입니다.");
                                ProductInventory newInventory = ProductInventory.create(option, warehouse, quantity, safetyQuantity);

                                // 최초 상품에 대한 부분은 둘다 상품에 대한 부분이 PREPARING으로 되어 있기 때문에 재고 등록시 ONSALE로 변경
                                product.onSale();
                                option.onSale();
                                return newInventory;
                            });

            inventoryRepository.save(inventory);
            log.info("[상품 입고 또는 재고 추가 완료] 상품 옵션 ID : {}, 창고 ID : {}, 입고 수량 : {}", option.getId(), warehouse.getId(), quantity);
            // 상품 입/출고에 대한 내역 저장
            // stockMovementRepository.save(stockMovement); // 여기에 대한 부분은 나중에 기능 구현

        } catch (Exception e) {
            log.error("[입고 과정중 오류 발생] 에러 메세지 : {}", e.getMessage());
            throw e;

        }
    }
}