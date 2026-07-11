package com.sh.aicommerce.card.repository;


import com.sh.aicommerce.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<Card, Long> {
    Optional<Card> findByIdAndMemberId(Long cardId, Long memberId);
}
