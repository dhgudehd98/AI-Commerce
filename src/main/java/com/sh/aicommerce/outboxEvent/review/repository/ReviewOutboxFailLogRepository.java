package com.sh.aicommerce.outboxEvent.review.repository;

import com.sh.aicommerce.entity.ReviewOutboxFailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ReviewOutboxFailLogRepository extends JpaRepository<ReviewOutboxFailLog, Long> {
}
