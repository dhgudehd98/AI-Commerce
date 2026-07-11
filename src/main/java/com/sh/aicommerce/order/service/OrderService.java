package com.sh.aicommerce.order.service;


import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.common.exception.auth.AuthException;
import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.entity.Member;
import com.sh.aicommerce.entity.ProductOption;
import com.sh.aicommerce.entity.ProductVariant;
import com.sh.aicommerce.order.dto.OrderSheetPriceDto;
import com.sh.aicommerce.order.dto.OrderSheetProductDto;
import com.sh.aicommerce.order.dto.OrderSheetResponseDto;
import com.sh.aicommerce.order.dto.Receiver;
import com.sh.aicommerce.product.repository.ProductVariantRepository;
import com.sh.aicommerce.productOption.repository.ProductOptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final AuthRepository authRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductVariantRepository productVariantRepository;


    public OrderSheetResponseDto orderSheet(Long memberId, Long variantId, Long optionId) {
        // 회원에 대한 정보 검사 ->
        Member member = authRepository.findById(memberId).orElseThrow(() -> new AuthException("존재하지 않는 회원입니다."));
//        ProductVariant variant = productVariantRepository.findWithProductById(variantId).orElseThrow(() -> new ProductException("존재하지 않는 상품입니다."));

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


        return new OrderSheetResponseDto(receiver, orderSheetProductDto, orderSheetPriceDto);
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
}