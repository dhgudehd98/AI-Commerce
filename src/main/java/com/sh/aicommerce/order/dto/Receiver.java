package com.sh.aicommerce.order.dto;

import com.sh.aicommerce.entity.Member;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Receiver {

    private String name;
    private String email;
    private String phone;
    private String zipCode;
    private String address;
    private String addressDetail;
    private ReceiverMethod receiverMethod; // 받는 방법
    private String deliveryRequestMessage; // 받는 방법이 직접 입력인 경우

    public Receiver(Member member) {
        this.name = member.getMemberName();
        this.email = member.getEmail();
        this.phone = member.getPhone();
        this.zipCode = member.getZipCode();
        this.address = member.getAddress();
        this.addressDetail = member.getAddressDetail();
        this.receiverMethod = ReceiverMethod.DOOR;
    }
}