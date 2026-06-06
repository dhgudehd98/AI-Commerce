package com.sh.aicommerce.auth.controller;

import com.sh.aicommerce.auth.dto.request.AuthJoinRequestDto;
import com.sh.aicommerce.auth.dto.request.AuthLoginRequestDto;
import com.sh.aicommerce.auth.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody AuthLoginRequestDto requestDto,
            HttpServletResponse response
    ) {
        return authService.login(requestDto, response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            Authentication authentication,
            HttpServletResponse response
    ) {
        return authService.logout((Long)authentication.getPrincipal(), response);
    }

    @PostMapping("/join")
    public Map<String,String> join(@RequestBody AuthJoinRequestDto authJoinRequestDto) {
        return authService.join(authJoinRequestDto);
    }
}