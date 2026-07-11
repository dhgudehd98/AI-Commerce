package com.sh.aicommerce.common.exception;


import com.sh.aicommerce.common.exception.auth.AuthException;
import com.sh.aicommerce.common.exception.brand.BrandException;
import com.sh.aicommerce.common.exception.card.CardException;
import com.sh.aicommerce.common.exception.member.MemberException;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.common.exception.search.SearchException;
import com.sh.aicommerce.common.exception.search.WeatherException;
import com.sh.aicommerce.common.exception.wms.InventoryException;
import com.sh.aicommerce.common.exception.wms.WarehouseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.security.auth.login.AccountException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {


    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<?> handlePaymentException(PaymentException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("result", "N", "message", e.getMessage()));
    }
    @ExceptionHandler(AccountException.class)
    public ResponseEntity<?> handleAccountException(AccountException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("result", "N", "message", e.getMessage()));
    }
    @ExceptionHandler(CardException.class)
    public ResponseEntity<?> handleCardException(CardException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("result", "N", "message", e.getMessage()));
    }
    @ExceptionHandler(WeatherException.class)
    public ResponseEntity<?> handleWeatherException(WeatherException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("result", "N", "message", e.getMessage()));
    }

    @ExceptionHandler(SearchException.class)
    public ResponseEntity<?> handleSearchException(SearchException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("result", "N", "message", e.getMessage()));
    }

    @ExceptionHandler(InventoryException.class)
    public ResponseEntity<?> handleInventoryException(InventoryException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("result", "N", "message", e.getMessage()));
    }
    @ExceptionHandler(WarehouseException.class)
    public ResponseEntity<?> handleWarehouseException(WarehouseException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("result", "N", "message", e.getMessage()));
    }
    @ExceptionHandler(ProductException.class)
    public ResponseEntity<?> handleProductException(ProductException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("result", "N", "message", e.getMessage()));
    }
    @ExceptionHandler(BrandException.class)
    public ResponseEntity<?> handleBrandException(BrandException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("result", "N", "message", e.getMessage()));
    }
    @ExceptionHandler(com.sh.aicommerce.common.exception.auth.AuthException.class)
    public ResponseEntity<?> handleAuthException(AuthException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("result", "N", "message", e.getMessage()));
    }

    @ExceptionHandler(MemberException.class)
    public ResponseEntity<Map<String, String>> handleMember(MemberException e) {
        Map<String, String> res = new HashMap<>();
        res.put("result", "N");
        res.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(res);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {

        log.error("[Exception Error Message] : " + e.getMessage());
        Map<String, String> response = new HashMap<>();
        response.put("result", "N");
        response.put("message", "시스템 오류가 발생했습니다. 관리자에게 문의해주세요.");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

}