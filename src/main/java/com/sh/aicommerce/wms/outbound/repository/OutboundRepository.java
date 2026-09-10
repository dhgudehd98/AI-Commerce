package com.sh.aicommerce.wms.outbound.repository;

import com.sh.aicommerce.entity.Outbound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboundRepository extends JpaRepository<Outbound, Long> {
}
