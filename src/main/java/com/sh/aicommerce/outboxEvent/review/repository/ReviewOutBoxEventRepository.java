package com.sh.aicommerce.outboxEvent.review.repository;

import com.sh.aicommerce.entity.ReviewOutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewOutBoxEventRepository extends JpaRepository<ReviewOutboxEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
    """
    select e
    from ReviewOutboxEvent e
    where e.publishStatus in ('PENDING', 'RETRY_WAIT')
    and e.publishAttemptCount < 3
    """
    )
    List<ReviewOutboxEvent> findPublishingTargets();
}
