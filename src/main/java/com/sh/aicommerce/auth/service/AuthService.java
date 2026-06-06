package com.sh.aicommerce.auth.service;

import com.sh.aicommerce.common.exception.auth.AuthException;
import com.sh.aicommerce.auth.dto.response.RefreshResponseDto;
import com.sh.aicommerce.auth.jwt.JwtUtil;
import com.sh.aicommerce.auth.dto.request.AuthJoinRequestDto;
import com.sh.aicommerce.auth.dto.request.AuthLoginRequestDto;
import com.sh.aicommerce.auth.dto.response.LoginResponseDto;
import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.common.exception.member.MemberException;
import com.sh.aicommerce.entity.Member;
import com.sh.aicommerce.redis.member.RedisLoginToken;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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

    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) throws AuthException {
        String refreshToken = getCookieValue(request, "refreshToken");
        return ResponseEntity.ok(refreshAccessToken(refreshToken));
    }

    private RefreshResponseDto refreshAccessToken(String refreshToken) throws AuthException{
        Claims claims;

        try {
            claims = jwtUtil.getRefreshTokenClaims(refreshToken);
        } catch (ExpiredJwtException e) {
            throw new AuthException("세션이 만료되었습니다. 다시 로그인해주세요.");
        }catch(JwtException | IllegalArgumentException e){
            log.error("[Refresh AccessToken] : " + e.getMessage());
            throw new AuthException("유효하지 않은 토큰입니다.");
        }

        // memberId에 대한 값 추출
        Long memberId = Long.parseLong(claims.getSubject());
        String savedRefreshToken = redisLoginToken.getRefreshToken(memberId).orElseThrow(() -> new AuthException("저장된 Refresh Token이 존재하지 않습니다."));

        // 쿠키에 저장되어 있는 refreshToken에 대한 값과 Redis에 저장되어 있는 refreshToken에 대한 값 비교
        if(!refreshToken.equals(savedRefreshToken)) throw new AuthException("토근에 대한 정보가 일치하지 않습니다.");

        String accessToken = jwtUtil.generateAccessToken(memberId);
        String nickName = authRepository.findById(memberId).get().getNickName();

        return new RefreshResponseDto("Y",nickName, accessToken);
    }

    private String getCookieValue(HttpServletRequest request, String cookieName) throws AuthException {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            throw new AuthException("쿠키가 존재하지 않습니다.");
        }

        for (Cookie cookie : cookies) {
            if(cookieName.equals(cookie.getName())) return cookie.getValue();
        }

        throw new AuthException("쿠키를 찾을 수 없습니다.");
    }
}