package com.sh.aicommerce.auth.repository;

import com.sh.aicommerce.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AuthRepository extends JpaRepository<Member, Long> {

    //! 나중에 회원가입에 대한 정보에서 MemberType에 대한 값 설정
    @Query(
            """
                    select m
                    from Member m
                    where m.id = :memberId
                    """)
    Optional<Member> findAdminById(@Param("memberId") Long memberId);
    Optional<Member> findByEmail(String email);

    boolean existsByCustomerKey(String customerKey);

    Optional<Member> findByCustomerKey(String customerKey);
}
