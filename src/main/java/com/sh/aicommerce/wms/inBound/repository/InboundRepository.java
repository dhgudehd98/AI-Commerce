package com.sh.aicommerce.wms.inBound.repository;

import com.sh.aicommerce.entity.Inbound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InboundRepository extends JpaRepository<Inbound, Long> {
}
