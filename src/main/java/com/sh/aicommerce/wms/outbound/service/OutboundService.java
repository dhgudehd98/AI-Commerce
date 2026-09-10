package com.sh.aicommerce.wms.outbound.service;

import com.sh.aicommerce.entity.Orders;
import com.sh.aicommerce.entity.Outbound;
import com.sh.aicommerce.entity.OutboundItem;
import com.sh.aicommerce.entity.Warehouse;
import com.sh.aicommerce.wms.outbound.repository.OutboundRepository;
import com.sh.aicommerce.wms.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboundService {

    private final OutboundRepository outboundRepository;
    private final WarehouseRepository warehouseRepository;
    @Transactional
    public void createOutbound(Orders order) {
        String outboundNumber = "outbound-1234";

        // 일단 임시로 설정
        Warehouse warehouse = warehouseRepository.findById(1L).get();
        Outbound outbound = Outbound.createOutboundByOrder(outboundNumber,order, warehouse);

        outboundRepository.save(outbound);
    }
}