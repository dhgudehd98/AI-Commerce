package com.sh.aicommerce.toss;


import com.sh.aicommerce.toss.dto.request.TossPaymentRequestDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentSuccessResponseDto;
import com.sh.aicommerce.toss.service.TossAPIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/api/toss")
@RequiredArgsConstructor
@Slf4j
public class TossAPIController {

    private final TossAPIService tossAPIService;


    @GetMapping("/pay/success")
    public String paySuccess(
            @RequestParam("orderId") String orderNumber,
            @RequestParam("paymentKey") String paymentKey,
            @RequestParam("amount") Integer amount,
            Model model
    ) {
        log.info("[토스 페이먼츠 결제 완료]");
        log.info("[토스 페이먼츠 응답 데이터] OrderNumber : {}, PaymentKey : {}. Amount : {}", orderNumber, paymentKey, amount);

        TossPaymentSuccessResponseDto tossPaymentSuccessResponseDto = tossAPIService.tossPaymentConfirm(new TossPaymentRequestDto(paymentKey, amount, orderNumber));
        model.addAttribute("payment", tossPaymentSuccessResponseDto);

        return "toss/pay-success";
    }

    @GetMapping("/pay/fail")
    public String payFail(
            @RequestParam("orderId") String orderNumber,
            @RequestParam("paymentKey") String paymentKey,
            @RequestParam("amount") Integer amount
    ) {
        log.info("[토스 페이먼츠 결제 실패]");
        log.info("[토스 페이먼츠 응답 데이터] OrderNumber : {}, PaymentKey : {}. Amount : {}", orderNumber, paymentKey, amount);

        return "toss/pay-fail";
    }

    @GetMapping("/card/set/success")
    public String cardSetSuccess(
            @RequestParam("variantId") Long variantId,
            @RequestParam("optionId") Long optionId,
            @RequestParam("customerKey") String customerKey,
            @RequestParam("authKey") String authKey
    ) {
        log.info("[토스 페이먼츠] 카드 등록 성공 응답 데이터 : customerKey : {}, authKey : {}", customerKey, authKey);
        tossAPIService.getBillingKey(customerKey, authKey);

        return "redirect:/api/order/sheet/" + variantId
                + "?optionId=" + optionId;
    }

    @GetMapping("/card/set/fail")
    public void cardSetFail(
            @RequestParam("code") String errorCode,
            @RequestParam("message") String errorMessage
    ) {
        log.info("[토스 페이먼츠] 카드 등록 실패 에러코드 : {} , 에러 메세지 : {)", errorCode, errorMessage);
    }

}