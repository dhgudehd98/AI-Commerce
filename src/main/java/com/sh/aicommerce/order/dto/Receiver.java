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
    private String phone;
    private String zipCode;
    private String address;
    private String addressDetail;

    public Receiver(Member member) {
        this.name = member.getMemberName();
        this.phone = member.getPhone();
        this.zipCode = member.getZipCode();
        this.address = member.getAddress();
        this.addressDetail = member.getAddressDetail();
    }
}