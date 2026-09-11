package com.sh.aicommerce.wms.outbound.service;

import com.sh.aicommerce.entity.Orders;
import com.sh.aicommerce.entity.Outbound;
import com.sh.aicommerce.entity.Warehouse;
import com.sh.aicommerce.wms.outbound.dto.response.OutboundDetailResponseDto;
import com.sh.aicommerce.wms.outbound.dto.response.OutboundSummaryResponseDto;
import com.sh.aicommerce.wms.outbound.repository.OutboundRepository;
import com.sh.aicommerce.wms.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    @Transactional
    public Page<OutboundSummaryResponseDto> getOutboundList(int page, int size) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        return outboundRepository.findOutboundAll(pageable)
                        .map(OutboundSummaryResponseDto::from);

//        return ResponseEntity.ok(
//                outboundRepository.findOutboundAll(pageable)
//                        .map(OutboundSummaryResponseDto::from)
//        );

    }

    public OutboundDetailResponseDto getOutboundDetail(Long outboundId) {
        log.info("[출고 상세정보 요청 : ] OutboundId : ", outboundId);
        return new OutboundDetailResponseDto();
    }
}