package com.sh.aicommerce.auth.service;

import com.sh.aicommerce.auth.dto.request.AuthJoinRequestDto;
import com.sh.aicommerce.auth.dto.request.AuthLoginRequestDto;
import com.sh.aicommerce.auth.dto.response.LoginResponseDto;
import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.common.exception.member.MemberException;
import com.sh.aicommerce.entity.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthRepository authRepository;
    private final PasswordEncoder encoder;

    @Transactional
    public ResponseEntity<?> login(AuthLoginRequestDto loginRequestDto) {
        Member member = authRepository.findByEmail(loginRequestDto.getEmail()).orElseThrow(() -> new MemberException("이메일 정보가 올바르지 않습니다."));

        if (!encoder.matches(loginRequestDto.getPasswd(), member.getPasswd())) {
            throw new MemberException("비밀번호가 올바르지 않습니다.");
        }

        //JWT 토큰 설정
        String accessToken = "";
        return ResponseEntity.ok(new LoginResponseDto("Y", "정상적으로 로그인이 완료되었습니다.", member.getNickName(), accessToken));
    }

    public Map<String, String> join(AuthJoinRequestDto joinRequestDto) {
        try {
            String passwd = encoder.encode(joinRequestDto.getPasswd()); // 비밀번호 암호화

            joinRequestDto.setPasswd(passwd);
            authRepository.save(new Member(joinRequestDto));

            return Map.of("result", "Y", "msg", "회원가입이 성공적으로 완료되었습니다.");
        } catch (Exception e) {
            log.error("[Auth Join Error] : " + e.getMessage());
            return Map.of("result", "N", "msg", "회원가입에 실패했습니다.");
        }
    }
}