package com.sh.aicommerce.entity;


import com.sh.aicommerce.auth.dto.request.AuthJoinRequestDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_member_email", columnNames = "email")
        }
)
@NoArgsConstructor
@Getter
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @OneToMany(mappedBy = "member")
    private List<Orders> orders = new ArrayList<>();

    @Column(nullable = false, unique = true)
    private String customerKey;

    @Column(nullable = false)
    private String memberName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String email; // 실제 로그인 할 떄 사용하는 이메일

    @Column(nullable = false)
    private String passwd;

    @Column(nullable = false)
    private String nickName; // 별명

    private String zipCode;
    private String address;
    private String addressDetail;

    public Member(AuthJoinRequestDto authJoinRequestDto, String customerKey) {
        this.memberName = authJoinRequestDto.getMemberName();
        this.customerKey = customerKey;
        this.phone = authJoinRequestDto.getPhone();
        this.email = authJoinRequestDto.getEmail();
        this.passwd = authJoinRequestDto.getPasswd();
        this.nickName = authJoinRequestDto.getNickName();
        this.zipCode = authJoinRequestDto.getZipCode();
        this.address = authJoinRequestDto.getAddress();
        this.addressDetail = authJoinRequestDto.getAddressDetail();
    }
}
