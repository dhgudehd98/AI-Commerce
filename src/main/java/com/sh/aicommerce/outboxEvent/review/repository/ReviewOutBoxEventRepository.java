package com.sh.aicommerce.outboxEvent.review.repository;

import com.sh.aicommerce.entity.ReviewOutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewOutBoxEventRepository extends JpaRepository<ReviewOutboxEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
    """
    select e
    from ReviewOutboxEvent e
    where e.publishStatus = 'PENDING'
    and e.publishAttemptCount < 3
    """
    )
    List<ReviewOutboxEvent> findPublishingTargets();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select e
            from ReviewOutboxEvent e
            where e.publishStatus = 'PUBLISHING'
            and e.processingStartedAt < :expiredAt
            """
    )
    List<ReviewOutboxEvent> findExpiredPublishingReviewEvents(@Param("expiredAt") LocalDateTime expiredAt);
}
