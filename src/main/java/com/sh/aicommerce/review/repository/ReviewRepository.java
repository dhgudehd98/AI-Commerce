package com.sh.aicommerce.review.repository;

import com.sh.aicommerce.entity.Review;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByOrderItemId(Long orderItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select r
    from Review r
    where r.orderItem.id = :orderItemId
      and r.editUsed = false
      and r.status = 'ACTIVE'
    """)
    Optional<Review> findForUpdate(@Param("orderItemId")Long orderItemId);

}
