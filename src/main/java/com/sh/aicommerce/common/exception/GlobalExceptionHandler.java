package com.sh.aicommerce.common.exception;


import com.sh.aicommerce.common.exception.member.MemberException;
import jakarta.security.auth.message.AuthException;
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

    @ExceptionHandler(MemberException.class)
    public ResponseEntity<Map<String, String>> handleMember(MemberException e) {
        Map<String, String> res = new HashMap<>();
        res.put("result", "N");
        res.put("message", e.getMessage());
        return ResponseEntity.badRequest().body(res);
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