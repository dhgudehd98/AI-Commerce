package com.sh.aicommerce.order.dto;

public enum ReceiverMethod {
    NONE, // 요청사항 없음
    DOOR, // 문앞 -> Default 값으로 설정
    SECURITY_OFFICE, // 경비실
    CUSTOM // 직접 입력
}