package com.sh.aicommerce.orderItem.service;


import com.sh.aicommerce.common.exception.orderItem.OrderItemException;
import com.sh.aicommerce.entity.OrderItem;
import com.sh.aicommerce.orderItem.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderItemService {
    private final OrderItemRepository orderItemRepository;

    /**
     * 주문 상품 유효성 검사
     * - Order : PAID(결제 완료)
     * - DeliveryStatus : DELIVERED(배송 완료)
     * @param memberId
     * @param orderItemId
     * @return
     */
    public OrderItem validateOrderItemPaidOrder(Long memberId, Long orderItemId) {
        OrderItem orderItem = orderItemRepository.validateOrderItemPaidOrderByMemberIdAndOrderItemId(memberId, orderItemId).orElseThrow(() -> new OrderItemException("구매 확정된 상품이 아니라 리뷰를 등록할 수 없습니다."));

        return orderItem;
    }
}