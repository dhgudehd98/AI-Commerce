package com.sh.aicommerce.common.exception;


import com.sh.aicommerce.common.exception.auth.AuthException;
import com.sh.aicommerce.common.exception.brand.BrandException;
import com.sh.aicommerce.common.exception.member.MemberException;
import com.sh.aicommerce.common.exception.product.ProductException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

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
        e.printStackTrace();
        log.error("Error Message : {}", e.getMessage());
        Map<String, String> response = new HashMap<>();
        response.put("result", "N");
        response.put("message", "시스템 오류가 발생했습니다. 관리자에게 문의해주세요.");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

}