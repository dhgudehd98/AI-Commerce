package com.sh.aicommerce.order.service;


import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.common.exception.auth.AuthException;
import com.sh.aicommerce.common.exception.order.OrderException;
import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.common.exception.wms.OutboundException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.order.dto.*;
import com.sh.aicommerce.order.orderRepository.OrderRepository;
import com.sh.aicommerce.orderCancellation.repository.OrderCancellationRepository;
import com.sh.aicommerce.product.repository.ProductVariantRepository;
import com.sh.aicommerce.productOption.repository.ProductOptionRepository;
import com.sh.aicommerce.wms.outbound.enums.OutboundStatus;
import com.sh.aicommerce.wms.outbound.repository.OutboundRepository;
import com.sh.aicommerce.wms.outboundHistory.repository.OutboundHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final AuthRepository authRepository;
    private final ProductOptionRepository productOptionRepository;
    private final OrderRepository orderRepository;
    private final OutboundRepository outboundRepository;
    private final OrderCancellationRepository orderCancellationRepository;
    private final OutboundHistoryRepository historyRepository;
    private final ProductVariantRepository productVariantRepository;



    public OrderSheetResponseDto orderSheet(Long memberId, Long variantId, Long optionId) {
        // 회원에 대한 정보 검사 ->
        Member member = validateMember(memberId);
//        ProductVariant variant = productVariantRepository.findWithProductById(variantId).orElseThrow(() -> new ProductException("존재하지 않는 상품입니다."));

        // 중복 결제를 방지하기 위한 주문 번호(orderNumber) 생성
        String orderNumber = createOrderNumber(optionId);

        // 상품 Variant, 상품 Option 정합성 검증(DB <-> ES)
        ProductOption option = productOptionRepository.findWithProductVariantByVariantIdAndOptionId(variantId, optionId).orElseThrow(() -> new ProductException("잘못된 상품 정보입니다. 상품정보를 다시 확인해주세요."));
        ProductVariant variant = option.getProductVariant();

        int availableQuantity = option.getInventories().stream()
                .mapToInt(inventory -> inventory.getAvailableQuantity())
                .sum();

        if(availableQuantity <= 0) throw new ProductException("상품 재고가 존재하지 않습니다.");

        Receiver receiver = new Receiver(member);
        OrderSheetProductDto orderSheetProductDto = new OrderSheetProductDto(variant, option);

        Integer deliveryPrice = setDeliveryPrice(orderSheetProductDto.getProductPrice());

        // 여기는 나중에 Coupon에 대한 값이 존재하면 설정하도록 변경 일단은 discountPrice에 대한 값은 0으로
        OrderSheetPriceDto orderSheetPriceDto = new OrderSheetPriceDto(orderSheetProductDto.getProductPrice(), deliveryPrice, 0, 0, orderSheetProductDto.getProductPrice() + deliveryPrice);
        return new OrderSheetResponseDto(receiver, orderSheetProductDto, orderSheetPriceDto, member.getCustomerKey(), orderNumber);
    }

    private  String createOrderNumber(Long optionId) {
        String orderedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase();

        return "ORD-" + orderedAt + "-" + optionId + "-" + suffix;
    }

    private int setDeliveryPrice(Integer productPrice) {
        if (productPrice >= 300000) {
            return 0;
        } else if (productPrice >= 200000) {
            return 3000;
        } else {
            return 5000;
        }
    }

    @Transactional
    public OrderCancelResponseDto cancel(Long memberId, Long orderId, OrderCancelRequestDto requestDto) {
        log.info("[주문 취소 요청] 주문 ID : {}", orderId);
        try {
            Member member = validateMember(memberId);
            Orders order = orderRepository.findByMemberIdForUpdate(memberId, orderId)
                    .orElseThrow(() -> new OrderException("본인 주문만 취소할 수 있습니다."));

            // 주문 취소 요청 -> 중복 처리 검사
            Optional<OrderCancellation> existCancel = orderCancellationRepository.findByOrderId(order.getId());
            if (existCancel.isPresent()) {
                return new OrderCancelResponseDto(
                        "Y",
                        "이미 접수된 취소 요청입니다. 현재 처리 상태 : " + existCancel.get().getCancelStatus().name()
                );
            }

            // 출고 상태 확인
            Outbound outbound = outboundRepository.findOutboundStatusBeforePackedByOrderIdForUpdate(order.getId())
                    .orElseThrow(() -> new OutboundException("현재 출고 상태에서는 주문 취소 요청이 불가능합니다. 고객센터에 문의해주세요."));

            // 출고 상태 취소 요청으로 변경
            outbound.updateStatusToCancelRequest();
            // 주문상태 취소 요청으로 변경
            order.updateStatusToCancelRequest();

            // 주문 취소 요청 Entity 생성
            // 상품 복귀 확인 여부
            boolean itemReturnRequired = switch (outbound.getOutBoundStatus()) {
                case PENDING, ON_HOLD -> false;
                case PICKING, PICKED, PACKING, PACKED -> true;
                default -> throw new OutboundException(
                        "현재 출고 상태에서는 취소할 수 없습니다."
                );
            };
            OrderCancellation orderCancellation = new OrderCancellation(order, requestDto.getReason(), itemReturnRequired);

            outboundRepository.save(outbound);
            orderRepository.save(order);
            orderCancellationRepository.save(orderCancellation);

            log.info("[주문 취소 완료] 주문 번호 : {} , 출고 번호 : {}", orderId, outbound.getId());

            return new OrderCancelResponseDto("Y", "주문 취소 요청이 완료되었습니다.");
        } catch (Exception e) {
            log.error("[주문 취소 처리 오류 발생] 주문 ID : {}", orderId);
            log.error(e.getMessage());
            throw new IllegalArgumentException("시스템 오류가 발생하였습니다. 관리자에게 문의해주세요.");
        }
    }

    private Member validateMember(Long memberId) {
        return authRepository.findById(memberId).orElseThrow(() -> new AuthException("존재하지 않는 회원입니다."));
    }

    public Orders validateOrderWithLock(Long orderId) {
        return orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new OrderException("해당 ID에 일치하는 주문번호가 존재하지 않습니다." + orderId));
    }
}