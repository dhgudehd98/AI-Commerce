package com.sh.aicommerce.admin.service;

import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.common.exception.auth.AuthException;
import com.sh.aicommerce.entity.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final AuthRepository authRepository;

    public Member validateAdminMemberByMemberId(Long memberId) {
        return authRepository.findAdminById(memberId)
                .orElseThrow(() -> new AuthException("해당 ID에 일치하는 관리자 정보가 존재하지 않습니다."));
    }
}