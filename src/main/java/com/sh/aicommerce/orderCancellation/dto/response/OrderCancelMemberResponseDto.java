package com.sh.aicommerce.orderCancellation.dto.response;

import com.sh.aicommerce.entity.Member;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderCancelMemberResponseDto {
    private Long memberId;
    private String memberName;

    public OrderCancelMemberResponseDto(Member member) {
        this.memberId = member.getId();
        this.memberName = member.getMemberName();
    }
}