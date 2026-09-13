package com.sh.aicommerce.wms.outboundHistory.repository;

import com.sh.aicommerce.entity.OutboundHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboundHistoryRepository extends JpaRepository<OutboundHistory, Long> {
}