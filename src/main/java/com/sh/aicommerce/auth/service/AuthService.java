package com.sh.aicommerce.auth.service;

import com.sh.aicommerce.auth.jwt.JwtUtil;
import com.sh.aicommerce.auth.dto.request.AuthJoinRequestDto;
import com.sh.aicommerce.auth.dto.request.AuthLoginRequestDto;
import com.sh.aicommerce.auth.dto.response.LoginResponseDto;
import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.common.exception.member.MemberException;
import com.sh.aicommerce.entity.Member;
import com.sh.aicommerce.redis.member.RedisLoginToken;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
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

    private final RedisLoginToken redisLoginToken;
    private final AuthRepository authRepository;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public ResponseEntity<?> login(AuthLoginRequestDto loginRequestDto, HttpServletResponse response) {
        Member member = authRepository.findByEmail(loginRequestDto.getEmail()).orElseThrow(() -> new MemberException("이메일 정보가 올바르지 않습니다."));

        if (!encoder.matches(loginRequestDto.getPasswd(), member.getPasswd())) {
            throw new MemberException("비밀번호가 올바르지 않습니다.");
        }

        //JWT 토큰 설정
        // accessToken, refreshToken 생성
        String accessToken = jwtUtil.generateAccessToken(member.getId());
        String refreshToken = jwtUtil.generateRefreshToken(member.getId());

        // accessToken , refreshToken 저장
        redisLoginToken.setRefreshToken(refreshToken, member.getId());

        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true); // js 접근 불가
//        cookie.setSecure(true); // HTTPS만 전송
        cookie.setPath("/");
        cookie.setMaxAge(86400);
        response.addCookie(cookie);
        return ResponseEntity.ok(new LoginResponseDto("Y", "정상적으로 로그인이 완료되었습니다.", member.getNickName(), accessToken));
    }

    public ResponseEntity<?> logout(Long memberId, HttpServletResponse response) {
        redisLoginToken.deleteRefreshToken(memberId);

        Cookie deleteCookie = new Cookie("refreshToken", null);
        deleteCookie.setMaxAge(0);
        deleteCookie.setPath("/");
        response.addCookie(deleteCookie);

        return ResponseEntity.ok(Map.of("result", "Y", "message", "정상적으로 로그아웃이 완료되었습니다."));
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