package com.sh.aicommerce.wms.outbound.service;

import com.sh.aicommerce.admin.service.AdminService;
import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.common.exception.auth.AuthException;
import com.sh.aicommerce.common.exception.delivery.DeliveryException;
import com.sh.aicommerce.common.exception.wms.OutboundException;
import com.sh.aicommerce.delivery.repository.DeliveryRepository;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderStatus;
import com.sh.aicommerce.wms.outbound.dto.request.OutboundStatusRequestDto;
import com.sh.aicommerce.wms.outbound.dto.response.OutboundDetailResponseDto;
import com.sh.aicommerce.wms.outbound.dto.response.OutboundHistoryResponseDto;
import com.sh.aicommerce.wms.outbound.dto.response.OutboundStatusResponseDto;
import com.sh.aicommerce.wms.outbound.dto.response.OutboundSummaryResponseDto;
import com.sh.aicommerce.wms.outbound.enums.OutboundStatus;
import com.sh.aicommerce.wms.outbound.repository.OutboundRepository;
import com.sh.aicommerce.wms.outboundHistory.repository.OutboundHistoryRepository;
import com.sh.aicommerce.wms.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboundService {

    private final OutboundHistoryRepository historyRepository;
    private final OutboundRepository outboundRepository;
    private final WarehouseRepository warehouseRepository;
    private final AuthRepository authRepository;
    private final AdminService adminService;
    @Transactional
    public void createOutbound(Orders order) {
        String outboundNumber = "outbound-" + order.getId();

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

    @Transactional
    public OutboundDetailResponseDto getOutboundDetail(Long outboundId) {
        log.info("[출고 상세정보 요청 : ] OutboundId : ", outboundId);

        Outbound outbound = outboundRepository.findById(outboundId).orElseThrow(() -> new OutboundException("해당 ID에 일치하는 출고 정보가 존재하지 않습니다."));
        Orders order = outbound.getOrder();
        Delivery delivery = order.getDelivery();

        if(delivery == null) throw new DeliveryException("주문번호에 일치하는 배송정보가 존재하지 않습니다.");

        return new OutboundDetailResponseDto(outbound, outbound.getOrder(), delivery);
    }

    @Transactional
    public OutboundStatusResponseDto changeOutboundStatus(Long memberId, OutboundStatus nextRequestStatus, Long outboundId) {
        log.info("[출고 상태 변경] 처리 관리자 memberId : {}", memberId);
        log.info("[NextRequestStatus] status : {}", nextRequestStatus);

        Member admin = adminService.validateAdminMemberByMemberId(memberId);
        Outbound outbound = outboundRepository
                .findByIdForUpdate(outboundId)
                .orElseThrow(() -> new OutboundException("해당 ID에 일치하는 출고 정보가 존재하지 않습니다."));
        // 출고 변경 내역에 저장하기 위해서 이전 출고 상태에 대한 값 추출
        log.info("Outbound Id : {}", outbound.getId());
        OutboundStatus previousStatus = outbound.getOutBoundStatus();

        if (outbound.isCancellationRequest()
                || outbound.getOrder().getStatus() == OrderStatus.CANCELED) {
            throw new OutboundException("취소 요청되었거나 취소된 주문입니다.");
        }

        log.info("previoustStatus : {} , nextRequestStatus : {}", previousStatus, nextRequestStatus);

        // 이전 요청이랑 다음 요청하면 변경과 이력을 중복처리하지 않음.
        if (previousStatus == nextRequestStatus) {
            return new OutboundStatusResponseDto(
                    outbound.getId(),
                    previousStatus.getStatus()
            );
        }

        OutboundStatus allowedNextStatus = switch (previousStatus) {
            case PENDING -> OutboundStatus.PICKING;
            case PICKING -> OutboundStatus.PICKED;
            case PICKED -> OutboundStatus.PACKING;
            case PACKING -> OutboundStatus.PACKED;
            case PACKED -> OutboundStatus.DELIVERY_REQUEST;
            default -> throw new OutboundException(
                    "현재 상태에서는 출고 작업을 진행할 수 없습니다.");
        };

        if (nextRequestStatus != allowedNextStatus) {
            log.info("requestNextStatus : {}", nextRequestStatus);
            throw new OutboundException("출고작업 순서가 올바르지 않습니다.");
        }

        if (nextRequestStatus == OutboundStatus.DELIVERY_REQUEST) {
            //인계요청 처리 -> 뭐 택배 API 연결하더지 pub/sub쪽에 저장하든지
            log.info("[택배사 인계 요청] 출고 ID : {} , 담당 관리자 : {}", outboundId, memberId);
        }

        log.info("Update Next Status : {}", allowedNextStatus);
        outbound.changeOutboundStatus(allowedNextStatus);
        outboundRepository.saveAndFlush(outbound);

        log.info("[출고 상태 변경] 이전 상태 : {} 변경 후 상태 : {} 변경 관리자 ID : {}", previousStatus.getStatus(), allowedNextStatus, memberId);

        OutboundHistory history = OutboundHistory
                .createOutboundHistoryByStatusChange(admin, outbound, previousStatus);

        historyRepository.save(history);

        return new OutboundStatusResponseDto(history);

    }

    public Page<OutboundHistoryResponseDto> getOutboundHistories(Long memberId, Long outboundId, int page, int size) {
        Member admin = adminService.validateAdminMemberByMemberId(memberId);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "id")
        );

        if(!existsOutbound(outboundId)) throw new OutboundException("해당 ID의 출고정보가 존재하지 않습니다.");

        return historyRepository.findByOutboundId(outboundId, pageable)
                .map(OutboundHistoryResponseDto::new);
    }

    private boolean existsOutbound(Long outboundId) {
        return outboundRepository.existsById(outboundId);
    }
}