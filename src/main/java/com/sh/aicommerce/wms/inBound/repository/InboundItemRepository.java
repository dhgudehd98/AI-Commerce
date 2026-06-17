package com.sh.aicommerce.wms.inBound.repository;

import com.sh.aicommerce.entity.InboundItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InboundItemRepository extends JpaRepository<InboundItem, Long> {
}
