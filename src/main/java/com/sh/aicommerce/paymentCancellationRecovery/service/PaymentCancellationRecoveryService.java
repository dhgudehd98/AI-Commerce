package com.sh.aicommerce.paymentCancellationRecovery.service;


import com.sh.aicommerce.common.exception.toss.TossPaymentException;
import com.sh.aicommerce.common.exception.toss.TossRestClientException;
import com.sh.aicommerce.paymentCancellation.repository.PaymentCancellationRepository;
import com.sh.aicommerce.paymentCancellationRecovery.dto.request.PaymentCancellationRequestDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentRequestHistoryResponseDto;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCancellationRecoveryService {

    private final PaymentCancellationRepository paymentCancellationRepository;
    private final PaymentCancellationRecoveryTxService recoveryTxService;
    private final TossPaymentClient client;
    public void recover(Long paymentCancellationId) {
        try {
            recoverPaymentCancellation(paymentCancellationId);
        } catch (RuntimeException e) {
            log.error("");
        }
    }

    private void recoverPaymentCancellation(Long paymentCancellationId) {

        PaymentCancellationRequestDto dto = recoveryTxService.validateAndCreateRecovery(paymentCancellationId);

        if (dto == null) {
            return;
        }

        Long historyId = recoveryTxService.startPaymentCancellationRecovery(dto.getRecoveryId());

        if (historyId == null) {
            return;
        }

        try {
            TossPaymentRequestHistoryResponseDto response = client.findPaymentHistory(dto.getOrderNumber());

            // 취소 내역 null·빈 목록 여부도 트랜잭션 서비스에서 검증
            recoveryTxService.applyPaymentCancellationResult(historyId, dto, response);
        } catch (TossPaymentException paymentException) {
            String errorCode = paymentException.getErrorCode();
        } catch (TossRestClientException restClientException) {

        } catch (RuntimeException exception) {

        }
    }
}